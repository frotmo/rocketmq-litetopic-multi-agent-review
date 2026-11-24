package edu.lab.review.messaging;

import com.alibaba.fastjson.JSON;
import edu.lab.review.config.LabConfig;
import edu.lab.review.model.Dimension;
import edu.lab.review.model.ReviewResult;
import org.apache.rocketmq.a2a.common.model.RocketMQResponse;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.message.Message;
import org.apache.rocketmq.client.apis.producer.Producer;
import org.apache.rocketmq.client.apis.producer.SendReceipt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;

public class ResultPublisher {
    private static final Logger log = LoggerFactory.getLogger(ResultPublisher.class);

    private final LabConfig config;
    private final Producer producer;
    private final ClientServiceProvider provider;

    public ResultPublisher(LabConfig config, Producer producer, ClientServiceProvider provider) {
        this.config = config;
        this.producer = producer;
        this.provider = provider;
    }

    /**
     * Publishes the A2A RocketMQResponse envelope to the LITE parent topic. The session id is
     * carried as the LiteTopic, which is exactly the session-scoped result channel of the lab.
     */
    public void publish(ReviewResult result) throws ClientException {
        RocketMQResponse response = RocketMQResponse.builder()
            .requestId(result.getRequestId())
            .taskId(result.getTaskId())
            .contextId(result.getSessionId())
            .responseBody(JSON.toJSONString(result))
            .stream(false)
            .end(true)
            .build();

        Dimension dimension = Dimension.valueOf(result.getDimension());
        String key = config.key(result.getSessionId(), dimension, "RES");
        Message message = provider.newMessageBuilder()
            .setTopic(config.resultTopic())
            .setLiteTopic(result.getSessionId())
            .setTag(dimension.name() + "_RESULT")
            .setKeys(key)
            .addProperty("studentId", config.getStudentId())
            .addProperty("sessionId", result.getSessionId())
            .addProperty("dimension", result.getDimension())
            .addProperty("stage", result.getStage())
            .addProperty("agentId", result.getAgentId())
            .setBody(JSON.toJSONString(response).getBytes(StandardCharsets.UTF_8))
            .build();
        SendReceipt receipt = producer.send(message);
        result.setMessageId(receipt.getMessageId().toString());
        log.info("[result] topic={} liteTopic={} tag={} key={} msgId={} dimension={} findings={}",
            config.resultTopic(), result.getSessionId(), dimension.name() + "_RESULT", key,
            result.getMessageId(), result.getDimension(), result.getFindings().size());
    }
}