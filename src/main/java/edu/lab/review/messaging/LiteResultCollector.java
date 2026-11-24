package edu.lab.review.messaging;

import com.alibaba.fastjson.JSON;
import edu.lab.review.config.LabConfig;
import edu.lab.review.config.RocketMqFacade;
import edu.lab.review.model.ReviewResult;
import org.apache.rocketmq.a2a.common.model.RocketMQResponse;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.consumer.ConsumeResult;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.OffsetOption;
import org.apache.rocketmq.client.apis.message.MessageView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

public class LiteResultCollector implements AutoCloseable {
    private static final Logger log = LoggerFactory.getLogger(LiteResultCollector.class);

    private final LabConfig config;
    private final String sessionId;
    private LitePushConsumer consumer;
    private final BlockingQueue<ReviewResult> queue = new LinkedBlockingQueue<>();

    private LiteResultCollector(LabConfig config, String sessionId) {
        this.config = config;
        this.sessionId = sessionId;
    }

    public static LiteResultCollector open(LabConfig config, RocketMqFacade facade, String sessionId)
        throws ClientException {
        LiteResultCollector collector = new LiteResultCollector(config, sessionId);
        collector.consumer = facade.newLiteConsumer(config.supervisorGroup(), collector::onMessage);
        collector.consumer.subscribeLite(sessionId, OffsetOption.MIN_OFFSET);
        log.info("[collector] subscribed LITE parent={} liteTopic={} group={} offset=MIN",
            config.resultTopic(), sessionId, config.supervisorGroup());
        return collector;
    }

    private ConsumeResult onMessage(MessageView view) {
        try {
            RocketMQResponse response = Envelopes.parseResponse(view);
            if (response.getResponseBody() == null) {
                return ConsumeResult.SUCCESS;
            }
            ReviewResult result = JSON.parseObject(response.getResponseBody(), ReviewResult.class);
            result.setMessageId(view.getMessageId().toString());
            if (!sessionId.equals(result.getSessionId())) {
                log.warn("[collector] ignore result from other session: expected={} actual={}",
                    sessionId, result.getSessionId());
                return ConsumeResult.SUCCESS;
            }
            queue.add(result);
            log.info("[collector] received dimension={} stage={} agent={} status={} findings={} msgId={} keys={}",
                result.getDimension(), result.getStage(), result.getAgentId(), result.getStatus(),
                result.getFindings().size(), view.getMessageId(), view.getKeys());
            return ConsumeResult.SUCCESS;
        } catch (Exception e) {
            log.error("[collector] failed to parse result message, msgId={}", view.getMessageId(), e);
            return ConsumeResult.SUCCESS;
        }
    }

    public List<ReviewResult> awaitDimensions(Set<String> dimensions, Duration timeout)
        throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        Map<String, ReviewResult> received = new LinkedHashMap<>();
        while (received.size() < dimensions.size()) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                break;
            }
            ReviewResult result = queue.poll(remaining, TimeUnit.NANOSECONDS);
            if (result == null) {
                break;
            }
            if ("DIMENSION".equals(result.getStage()) && dimensions.contains(result.getDimension())) {
                received.putIfAbsent(result.getDimension(), result);
                log.info("[collector] progress {}/{} dimension results", received.size(), dimensions.size());
            }
        }
        List<ReviewResult> ordered = new ArrayList<>();
        for (String dimension : dimensions) {
            ReviewResult result = received.get(dimension);
            if (result != null) {
                ordered.add(result);
            }
        }
        return ordered;
    }

    public ReviewResult awaitSummary(Duration timeout) throws InterruptedException {
        long deadline = System.nanoTime() + timeout.toNanos();
        while (true) {
            long remaining = deadline - System.nanoTime();
            if (remaining <= 0) {
                return null;
            }
            ReviewResult result = queue.poll(remaining, TimeUnit.NANOSECONDS);
            if (result == null) {
                return null;
            }
            if ("SUMMARY".equals(result.getStage())) {
                return result;
            }
        }
    }

    @Override
    public void close() {
        Thread closer = new Thread(() -> {
            try {
                consumer.unsubscribeLite(sessionId);
            } catch (Exception ignored) {
                // best effort
            }
            try {
                consumer.close();
            } catch (Exception ignored) {
                // best effort
            }
        }, "lite-collector-close");
        closer.setDaemon(true);
        closer.start();
        try {
            closer.join(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}