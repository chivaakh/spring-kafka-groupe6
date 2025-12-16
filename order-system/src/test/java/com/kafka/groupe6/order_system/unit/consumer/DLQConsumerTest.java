package com.kafka.groupe6.order_system.unit.consumer;

import java.util.List;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.junit.jupiter.MockitoExtension;

import com.kafka.groupe6.order_system.consumer.DLQConsumerService;
import com.kafka.groupe6.order_system.model.Order;

@ExtendWith(MockitoExtension.class)
class DLQConsumerTest {

    @InjectMocks
    private DLQConsumerService dlqConsumerService;

    @Test
    void shouldConsumeDLQMessageWithoutError() {

        // 1️⃣ Créer une commande de test
        Order order = new Order(
                "99",
                "C99",
                List.of("Item"),
                100.0,
                "ERROR",
                System.currentTimeMillis()
        );

        // 2️⃣ Créer un ConsumerRecord Kafka (simulation DLQ)
        ConsumerRecord<String, Order> record =
                new ConsumerRecord<>(
                        "orders-dlq", // topic
                        0,            // partition
                        0L,           // offset
                        "99",          // key
                        order          // value
                );

        // 3️⃣ Appel de la méthode réelle
        dlqConsumerService.consumeDLQMessage(record);

        // ✅ Test réussi si aucune exception n’est levée
    }
}
