package com.kafka.groupe6.order_system.integration;

import java.time.Duration;
import java.util.Arrays;
import java.util.Map;
import java.util.UUID;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.TestPropertySource;

import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;

/**
 * Tests d'intégration complets pour le système de commandes Kafka
 * 
 * Responsable: AYA (Tâche 5 - Tests d'intégration)
 * 
 * IMPORTANT: Utilise un port aléatoire pour éviter les conflits avec Docker Kafka
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    topics = { "orders-input", "orders-processed", "orders-dlq" }, 
    partitions = 1
)
@TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "spring.kafka.consumer.auto-offset-reset=earliest",
    "app.stock.simulate-failures=false"
})
@Disabled("Désactivé car Docker Kafka tourne - utiliser quand Docker est arrêté")
class CompleteKafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Autowired
    private OrderProducerService orderProducerService;

    private Consumer<String, Order> consumer;

    @AfterEach
    void cleanup() {
        if (consumer != null) {
            consumer.close();
        }
    }

    // ==================== TEST 1: FLUX BASIQUE ====================

    @Test
    @DisplayName("TEST 1: Producer envoie correctement vers orders-input")
    void shouldSendOrderToOrdersInput() {
        // Given: Créer une commande valide avec ID unique
        String uniqueId = "TEST-INPUT-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = new Order(
                uniqueId,
                "CUST-001",
                Arrays.asList("Laptop", "Mouse"),
                299.99,
                "PENDING",
                System.currentTimeMillis()
        );

        // When: Envoyer via OrderProducerService
        orderProducerService.sendOrder(order);

        // Then: Vérifier réception dans orders-input
        consumer = createConsumer("orders-input", "test-group-1");
        
        ConsumerRecord<String, Order> record = pollForSpecificOrder(consumer, uniqueId, 10);

        assertThat(record).isNotNull();
        assertThat(record.value().getId()).isEqualTo(uniqueId);
        assertThat(record.value().getCustomerId()).isEqualTo("CUST-001");
        assertThat(record.value().getTotalAmount()).isEqualTo(299.99);
        assertThat(record.key()).isEqualTo(uniqueId);
    }

    // ==================== TEST 2: CLÉ DU MESSAGE ====================

    @Test
    @DisplayName("TEST 2: La clé du message Kafka = ID de la commande")
    void shouldUseOrderIdAsKafkaKey() {
        // Given
        String uniqueId = "KEY-TEST-" + UUID.randomUUID().toString().substring(0, 8);
        Order order = new Order(
                uniqueId,
                "CUST-KEY-001",
                Arrays.asList("Product"),
                50.00,
                "PENDING",
                System.currentTimeMillis()
        );

        // When
        orderProducerService.sendOrder(order);

        // Then
        consumer = createConsumer("orders-input", "test-group-2");
        
        ConsumerRecord<String, Order> record = pollForSpecificOrder(consumer, uniqueId, 10);

        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(uniqueId);
        assertThat(record.key()).isEqualTo(record.value().getId());
    }

    // ==================== MÉTHODES UTILITAIRES ====================

    /**
     * Crée un consumer Kafka pour un topic donné avec un group.id unique
     */
    private Consumer<String, Order> createConsumer(String topic, String groupId) {
        Map<String, Object> consumerProps = 
                KafkaTestUtils.consumerProps(groupId, "true", embeddedKafkaBroker);

        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        consumerProps.put(JsonDeserializer.VALUE_DEFAULT_TYPE, Order.class.getName());

        Consumer<String, Order> consumer = new KafkaConsumer<>(consumerProps);
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, topic);

        return consumer;
    }

    /**
     * Poll pour trouver un message spécifique par son ID
     */
    private ConsumerRecord<String, Order> pollForSpecificOrder(
            Consumer<String, Order> consumer, 
            String orderId, 
            int timeoutSeconds) {
        
        long endTime = System.currentTimeMillis() + (timeoutSeconds * 1000L);
        
        while (System.currentTimeMillis() < endTime) {
            ConsumerRecords<String, Order> records = consumer.poll(Duration.ofSeconds(2));
            
            for (ConsumerRecord<String, Order> record : records) {
                if (record.value().getId().equals(orderId)) {
                    return record;
                }
            }
        }
        
        return null;
    }
}
