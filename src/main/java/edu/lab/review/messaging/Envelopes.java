package edu.lab.review.messaging;

import com.alibaba.fastjson.JSON;
import edu.lab.review.model.ReviewTask;
import org.apache.rocketmq.a2a.common.model.RocketMQRequest;
import org.apache.rocketmq.a2a.common.model.RocketMQResponse;
import org.apache.rocketmq.client.apis.message.MessageView;

import java.nio.charset.StandardCharsets;

public final class Envelopes {
    private Envelopes() {
    }

    public record ParsedTask(RocketMQRequest request, ReviewTask task) {
    }

    public static ParsedTask parseTask(MessageView view) {
        byte[] body = new byte[view.getBody().remaining()];
        view.getBody().get(body);
        RocketMQRequest request = JSON.parseObject(new String(body, StandardCharsets.UTF_8), RocketMQRequest.class);
        ReviewTask task = JSON.parseObject(request.getRequestBody(), ReviewTask.class);
        return new ParsedTask(request, task);
    }

    public static RocketMQResponse parseResponse(MessageView view) {
        byte[] body = new byte[view.getBody().remaining()];
        view.getBody().get(body);
        return JSON.parseObject(new String(body, StandardCharsets.UTF_8), RocketMQResponse.class);
    }

    public static String bodyOf(MessageView view) {
        byte[] body = new byte[view.getBody().remaining()];
        view.getBody().get(body);
        return new String(body, StandardCharsets.UTF_8);
    }
}