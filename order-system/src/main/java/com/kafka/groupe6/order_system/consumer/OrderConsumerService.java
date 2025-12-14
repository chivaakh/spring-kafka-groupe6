package com.kafka.groupe6.order_system.consumer;

import com.kafka.groupe6.order_system.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

@Service
public class OrderConsumerService {

    private static final Logger logger = 
        LoggerFactory.getLogger(OrderConsumerService.class);

    @KafkaListener(
        topics = "orders-input", 
        groupId = "order-consumer-group"
    )
    public void consumeOrder(
            @Payload Order order,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        logger.info("========================================");
        logger.info("Message reçu du topic 'orders-input'");
        logger.info("Partition: {}", partition);
        logger.info("Offset: {}", offset);
        logger.info("Timestamp: {}", timestamp);
        logger.info("----------------------------------------");
        logger.info("Order ID: {}", order.getId());
        logger.info("Customer ID: {}", order.getCustomerId());
        logger.info("Items: {}", order.getItems());
        logger.info("Total Amount: {}", order.getTotalAmount());
        logger.info("Status: {}", order.getStatus());
        logger.info("Order Timestamp: {}", order.getTimestamp());
        logger.info("========================================");
        
        // NOTE POUR EMANE: 
        // C'est ici qu'elle ajoutera la logique de traitement
        // Pour l'instant, on se contente de logger
    }
}