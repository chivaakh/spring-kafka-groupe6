package com.kafka.groupe6.order_system.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

@Configuration
public class KafkaTopicConfig {

    public static final String ORDERS_INPUT_TOPIC = "orders-input";
    public static final String ORDERS_PROCESSED_TOPIC = "orders-processed";
    public static final String ORDERS_DLQ_TOPIC = "orders-dlq";

    @Bean
    public NewTopic ordersInputTopic() {
        return TopicBuilder.name(ORDERS_INPUT_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersProcessedTopic() {
        return TopicBuilder.name(ORDERS_PROCESSED_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic ordersDlqTopic() {
        return TopicBuilder.name(ORDERS_DLQ_TOPIC)
                .partitions(1)
                .replicas(1)
                .build();
    }
}