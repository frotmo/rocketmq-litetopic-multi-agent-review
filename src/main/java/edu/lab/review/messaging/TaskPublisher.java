package edu.lab.review.messaging;

import com.alibaba.fastjson.JSON;
import edu.lab.review.config.LabConfig;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.ReviewTask;
import org.apache.rocketmq.a2a.common.model.RocketMQRequest;
import org.apache.rocketmq.a2a.common.uitl.RequestIdGenerator;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

public class TaskPublisher {
    private static final Logger log = LoggerFactory.getLogger(TaskPublisher.class);

    private final LabConfig config;
    private final Producer producer;
    private final ClientServiceProvider provider;

    public TaskPublisher(LabConfig config, Producer producer, ClientServiceProvider provider) {
        this.config = config;
        this.producer = producer;
        this.provider = provider;
    }

    public void dispatch(ReviewTask task) throws ClientException {
        if (task.getTaskId() == null || task.getTaskId().isBlank()) {
            task.setTaskId(task.getStudentId() + "-" + task.getDimension().toLowerCase() + "-"
                + UUID.randomUUID().toString().replace("-", "").substring(0, 8));
        }
        String requestId = RequestIdGenerator.nextId();
        task.setRequestId(requestId);
        task.setDispatchedAt(System.currentTimeMillis());

        RocketMQRequest request = RocketMQRequest.builder()
            .requestId(requestId)
            .destAgentTopic(config.taskTopic())
            .workAgentResponseTopic(config.resultTopic())
            .liteTopic(task.getSessionId())
            .requestBody(JSON.toJSONString(task))
            .addHeader("studentId", config.getStudentId())
            .addHeader("sessionId", task.getSessionId())
            .addHeader("dimension", task.getDimension())
            .addHeader("stage", task.getStage())
            .build();

        String key = config.key(task.getSessionId(), Dimension.valueOf(task.getDimension()), "REQ");
        Message message = provider.newMessageBuilder()
            .setTopic(config.taskTopic())
            .setTag(task.getDimension())
            .setKeys(key)
            .addProperty("studentId", config.getStudentId())
            .addProperty("sessionId", task.getSessionId())
            .addProperty("dimension", task.getDimension())
            .addProperty("stage", task.getStage())
            .addProperty("taskId", task.getTaskId())
            .setBody(JSON.toJSONString(request).getBytes(StandardCharsets.UTF_8))
            .build();
        SendReceipt receipt = producer.send(message);
        task.setMessageId(receipt.getMessageId().toString());
        log.info("[dispatch] topic={} tag={} group={} key={} msgId={} taskId={}",
            config.taskTopic(), task.getDimension(), groupFor(task.getDimension()), key,
            task.getMessageId(), task.getTaskId());
    }

    private String groupFor(String dimension) {
        return config.groupOf(Dimension.valueOf(dimension));
    }
}