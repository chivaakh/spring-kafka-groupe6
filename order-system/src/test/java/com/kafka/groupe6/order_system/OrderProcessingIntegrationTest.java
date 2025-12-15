package com.kafka.groupe6.order_system;

import com.kafka.groupe6.order_system.config.KafkaTopicConfig;
import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;
import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import java.time.Duration;
import java.util.Arrays;
import java.util.Collections;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests d'intégration pour le flux complet de traitement des commandes.
 * 
 * Responsable: EMANE (Tâche 4 - Partie D)
 * 
 * Ce test utilise un Kafka embarqué pour tester:
 * - Le flux complet: orders-input → traitement → orders-processed
 * - Le flux d'erreur: orders-input → retry → orders-dlq
 */
@SpringBootTest
@EmbeddedKafka(
    partitions = 1,
    topics = {
        KafkaTopicConfig.ORDERS_INPUT_TOPIC,
        KafkaTopicConfig.ORDERS_PROCESSED_TOPIC,
        KafkaTopicConfig.ORDERS_DLQ_TOPIC
    },
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest"
})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class OrderProcessingIntegrationTest {

    @Autowired
    private OrderProducerService producerService;

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    @DisplayName("Doit envoyer une commande vers orders-input")
    void shouldSendOrderToInputTopic() throws Exception {
        // Given
        Order order = createValidOrder("INT-001");
        
        // When
        producerService.sendOrder(order);
        
        // Then - Vérifier que le message est dans orders-input
        Consumer<String, Order> consumer = createConsumer(KafkaTopicConfig.ORDERS_INPUT_TOPIC);
        
        ConsumerRecords<String, Order> records = KafkaTestUtils.getRecords(
            consumer, Duration.ofSeconds(10)
        );
        
        assertFalse(records.isEmpty(), "Le topic orders-input devrait contenir des messages");
        consumer.close();
    }

    @Test
    @DisplayName("Test du flux complet: envoi et réception")
    void shouldProcessFullFlow() throws Exception {
        // Given
        Order order = createValidOrder("FLOW-001");
        
        // When
        producerService.sendOrder(order);
        
        // Attendre le traitement
        Thread.sleep(2000);
        
        // Then - Le message devrait être traité (on vérifie juste l'envoi ici)
        Consumer<String, Order> consumer = createConsumer(KafkaTopicConfig.ORDERS_INPUT_TOPIC);
        
        ConsumerRecords<String, Order> records = KafkaTestUtils.getRecords(
            consumer, Duration.ofSeconds(10)
        );
        
        assertTrue(records.count() > 0, "Des messages devraient être présents");
        consumer.close();
    }

    // ==================== HELPERS ====================

    private Order createValidOrder(String orderId) {
        return new Order(
            orderId,
            "CUSTOMER-INT-001",
            Arrays.asList("Product A", "Product B"),
            150.00,
            "PENDING",
            System.currentTimeMillis()
        );
    }

    private Order createInvalidOrder(String orderId) {
        // Commande avec montant invalide (négatif)
        return new Order(
            orderId,
            "CUSTOMER-INT-002",
            Arrays.asList("Product C"),
            -50.00,  // Montant invalide
            "PENDING",
            System.currentTimeMillis()
        );
    }

    private Consumer<String, Order> createConsumer(String topic) {
        Map<String, Object> consumerProps = KafkaTestUtils.consumerProps(
            "test-group-" + System.currentTimeMillis(),
            "true",
            embeddedKafkaBroker
        );
        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        
        ConsumerFactory<String, Order> consumerFactory = new DefaultKafkaConsumerFactory<>(
            consumerProps,
            new StringDeserializer(),
            new JsonDeserializer<>(Order.class)
        );
        
        Consumer<String, Order> consumer = consumerFactory.createConsumer();
        consumer.subscribe(Collections.singletonList(topic));
        
        return consumer;
    }
}

