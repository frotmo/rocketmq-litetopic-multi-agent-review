package edu.lab.review.config;

import org.apache.rocketmq.a2a.common.uitl.RocketMQUtil;
import org.apache.rocketmq.client.apis.ClientConfiguration;
import org.apache.rocketmq.client.apis.ClientConfigurationBuilder;
import org.apache.rocketmq.client.apis.ClientException;
import org.apache.rocketmq.client.apis.ClientServiceProvider;
import org.apache.rocketmq.client.apis.StaticSessionCredentialsProvider;
import org.apache.rocketmq.client.apis.consumer.FilterExpression;
import org.apache.rocketmq.client.apis.consumer.LitePushConsumer;
import org.apache.rocketmq.client.apis.consumer.MessageListener;
import org.apache.rocketmq.client.apis.consumer.PushConsumer;
import org.apache.rocketmq.client.apis.producer.Producer;

import java.util.Map;

public class RocketMqFacade implements AutoCloseable {
    private final LabConfig config;
    private final ClientServiceProvider provider;
    private final ClientConfiguration clientConfiguration;
    private final Producer producer;

    public RocketMqFacade(LabConfig config) throws ClientException {
        this.config = config;
        this.provider = ClientServiceProvider.loadService();
        ClientConfigurationBuilder builder = ClientConfiguration.newBuilder()
            .setEndpoints(config.getEndpoint())
            .enableSsl(config.isSsl());
        if (!config.getNamespace().isBlank()) {
            builder.setNamespace(config.getNamespace());
        }
        if (config.hasCredentials()) {
            builder.setCredentialProvider(new StaticSessionCredentialsProvider(
                config.getAccessKey(), config.getSecretKey()));
        }
        this.clientConfiguration = builder.build();
        this.producer = provider.newProducerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setTopics(config.taskTopic(), config.resultTopic())
            .build();
    }

    public Producer producer() {
        return producer;
    }

    public ClientServiceProvider provider() {
        return provider;
    }

    public ClientConfiguration clientConfiguration() {
        return clientConfiguration;
    }

    public PushConsumer newTagConsumer(String group, String tag, MessageListener listener) throws ClientException {
        return provider.newPushConsumerBuilder()
            .setClientConfiguration(clientConfiguration)
            .setConsumerGroup(group)
            .setSubscriptionExpressions(Map.of(config.taskTopic(), new FilterExpression(tag)))
            .setMessageListener(listener)
            .build();
    }

    /**
     * Uses the rocketmq-a2a SDK helper so the LiteTopic consumer settings stay aligned
     * with the SDK's A2A transport (LitePushConsumer bound to the parent result topic).
     */
    public LitePushConsumer newLiteConsumer(String group, MessageListener listener) throws ClientException {
        return RocketMQUtil.buildLitePushConsumer(config.getEndpoint(), config.getNamespace(),
            config.getAccessKey(), config.getSecretKey(), config.resultTopic(), group, listener);
    }

    @Override
    public void close() {
        Thread closer = new Thread(() -> {
            try {
                producer.close();
            } catch (Exception ignored) {
                // best-effort close
            }
        }, "producer-close");
        closer.setDaemon(true);
        closer.start();
        try {
            closer.join(3_000);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}