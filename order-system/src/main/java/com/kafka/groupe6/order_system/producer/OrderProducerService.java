package com.kafka.groupe6.order_system.producer;

import com.kafka.groupe6.order_system.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;

@Service
public class OrderProducerService {

    private static final Logger logger = LoggerFactory.getLogger(OrderProducerService.class);
    private static final String TOPIC = "orders-input";

    private final KafkaTemplate<String, Object> kafkaTemplate;

    // Constructeur pour l'injection
    public OrderProducerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void sendOrder(Order order) {
        String key = (order.getId() != null) ? order.getId() : "no-id";
        
        // Méthode moderne avec CompletableFuture
        kafkaTemplate.send(TOPIC, key, order)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        logger.info("Order sent successfully: {}", order);
                    } else {
                        logger.error("Failed to send order {}", order, ex);
                    }
                });
    }
}