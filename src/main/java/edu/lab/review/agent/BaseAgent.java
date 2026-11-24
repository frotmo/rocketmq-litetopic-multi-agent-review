package edu.lab.review.agent;

import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.messaging.Envelopes;
import edu.lab.review.messaging.ResultPublisher;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.ReviewResult;
import edu.lab.review.model.ReviewTask;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.CountDownLatch;

public abstract class BaseAgent implements AutoCloseable {
    protected final Logger log = LoggerFactory.getLogger(getClass());
    protected final LabConfig config;
    protected final RocketMqFacade facade;
    protected final Dimension dimension;
    protected final String agentId;
    protected final ResultPublisher resultPublisher;

    private final CountDownLatch closed = new CountDownLatch(1);
    private PushConsumer consumer;
    private volatile boolean running;

    protected BaseAgent(LabConfig config, RocketMqFacade facade, Dimension dimension) {
        this.config = config;
        this.facade = facade;
        this.dimension = dimension;
        this.agentId = toAgentId(dimension);
        this.resultPublisher = new ResultPublisher(config, facade.producer(), facade.provider());
    }

    public void start() throws ClientException {
        if (running) {
            return;
        }
        consumer = facade.newTagConsumer(config.groupOf(dimension), dimension.name(), this::onMessage);
        running = true;
        log.info("[agent:start] agent={} topic={} tag={} group={} resultTopic={} liteTopicPrefix={}",
            agentId, config.taskTopic(), dimension.name(), config.groupOf(dimension),
            config.resultTopic(), config.getStudentId());
    }

    private ConsumeResult onMessage(MessageView view) {
        long startedAt = System.currentTimeMillis();
        ReviewTask task = null;
        try {
            Envelopes.ParsedTask parsed = Envelopes.parseTask(view);
            task = parsed.task();
            validate(task);
            log.info("[agent:recv] agent={} session={} taskId={} dimension={} msgId={} keys={}",
                agentId, task.getSessionId(), task.getTaskId(), task.getDimension(),
                view.getMessageId(), view.getKeys());
            ReviewResult result = review(task, startedAt);
            result.setStudentId(config.getStudentId());
            result.setSessionId(task.getSessionId());
            result.setTaskId(task.getTaskId());
            result.setRequestId(task.getRequestId());
            result.setTaskMessageId(task.getMessageId());
            result.setDimension(dimension.name());
            result.setStage(task.getStage());
            result.setAgentId(agentId);
            result.setDispatchedAt(task.getDispatchedAt());
            result.setStartedAt(startedAt);
            result.setFinishedAt(System.currentTimeMillis());
            result.setDurationMs(result.getFinishedAt() - startedAt);
            result.setStats(ReviewResult.statsOf(result.getFindings()));
            customize(task, result);
            resultPublisher.publish(result);
            log.info("[agent:done] agent={} session={} taskId={} durationMs={} findings={}",
                agentId, task.getSessionId(), task.getTaskId(), result.getDurationMs(),
                result.getFindings().size());
            return ConsumeResult.SUCCESS;
        } catch (IllegalArgumentException validationError) {
            log.warn("[agent:skip] agent={} msgId={} reason={}", agentId, view.getMessageId(),
                validationError.getMessage());
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("[agent:error] agent={} msgId={}", agentId, view.getMessageId(), e);
            if (task != null) {
                try {
                    ReviewResult error = ReviewResult.error(task, agentId, e.getMessage(), startedAt);
                    error.setDispatchedAt(task.getDispatchedAt());
                    resultPublisher.publish(error);
                } catch (Exception publishError) {
                    log.error("[agent:error] failed to publish error result", publishError);
                }
            }
            return ConsumeResult.SUCCESS;
        }
    }

    private void validate(ReviewTask task) {
        if (task == null || task.getSessionId() == null || task.getStudentId() == null) {
            throw new IllegalArgumentException("missing task envelope fields");
        }
        if (!config.getStudentId().equals(task.getStudentId())) {
            throw new IllegalArgumentException("student id mismatch: " + task.getStudentId());
        }
        if (!dimension.name().equals(task.getDimension())) {
            throw new IllegalArgumentException("dimension mismatch: " + task.getDimension());
        }
        if (task.getCode() == null || task.getCode().isBlank()) {
            throw new IllegalArgumentException("empty code payload");
        }
    }

    protected abstract ReviewResult review(ReviewTask task, long startedAt) throws Exception;

    protected void customize(ReviewTask task, ReviewResult result) {
        // hook for agents that need the finalized timestamps (e.g. report rendering)
    }

    public void await() throws InterruptedException {
        closed.await();
    }

    public String agentId() {
        return agentId;
    }

    private static String toAgentId(Dimension dimension) {
        String lower = dimension.name().toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1) + "Agent";
    }

    @Override
    public void close() {
        running = false;
        if (consumer != null) {
            closeWithTimeout(consumer, 3_000);
        }
        closed.countDown();
    }

    private static void closeWithTimeout(AutoCloseable closeable, long timeoutMs) {
        Thread closer = new Thread(() -> {
            try {
                closeable.close();
            } catch (Exception ignored) {
                // best effort
            }
        }, "agent-close");
        closer.setDaemon(true);
        closer.start();
        try {
            closer.join(timeoutMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}