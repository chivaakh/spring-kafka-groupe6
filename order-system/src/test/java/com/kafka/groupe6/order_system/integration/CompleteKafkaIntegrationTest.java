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
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;
import org.springframework.test.annotation.DirtiesContext;

import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;

/**
 * Tests d'intégration complets pour le système de commandes Kafka
 * 
 * Responsable: AYA (Tâche 5 - Tests d'intégration)
 * 
 * IMPORTANT: Chaque test utilise un group.id unique pour éviter
 * la contamination entre tests.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@EmbeddedKafka(
    topics = { "orders-input", "orders-processed", "orders-dlq" }, 
    partitions = 1,
    brokerProperties = {
        "listeners=PLAINTEXT://localhost:9092",
        "port=9092"
    }
)
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

    // ==================== TEST 2: FLUX COMPLET ====================

    @Test
    @DisplayName("TEST 2: Flux complet - Commande valide traitée et publiée vers orders-processed")
    void shouldProcessValidOrderAndPublishToOrdersProcessed() throws InterruptedException {
        // Given
        String uniqueId = "VALID-" + UUID.randomUUID().toString().substring(0, 8);
        Order validOrder = new Order(
                uniqueId,
                "CUST-VALID-001",
                Arrays.asList("Product A", "Product B"),
                150.00,
                "PENDING",
                System.currentTimeMillis()
        );

        // When: Envoyer la commande
        orderProducerService.sendOrder(validOrder);

        // Attendre que le Consumer traite et publie vers orders-processed
        Thread.sleep(5000);

        // Then: Vérifier dans orders-processed
        consumer = createConsumer("orders-processed", "test-group-2");
        
        ConsumerRecord<String, Order> processedRecord = pollForSpecificOrder(consumer, uniqueId, 15);

        assertThat(processedRecord).isNotNull();
        assertThat(processedRecord.value().getId()).isEqualTo(uniqueId);
        assertThat(processedRecord.value().getStatus()).isEqualTo("COMPLETED");
    }

    // ==================== TEST 3: GESTION DES ERREURS - DLQ ====================

    @Test
    @DisplayName("TEST 3: Commande invalide routée vers DLQ après retries")
    void shouldRouteInvalidOrderToDLQAfterRetries() throws InterruptedException {
        // Given: Commande invalide (liste items vide + montant négatif)
        String uniqueId = "INVALID-" + UUID.randomUUID().toString().substring(0, 8);
        Order invalidOrder = new Order(
                uniqueId,
                "CUST-INVALID-001",
                Arrays.asList(), // ❌ Liste vide
                -50.00,          // ❌ Montant négatif
                "PENDING",
                System.currentTimeMillis()
        );

        // When: Envoyer la commande invalide
        orderProducerService.sendOrder(invalidOrder);

        // Attendre les retries (2-3 tentatives) + routage vers DLQ
        Thread.sleep(12000);

        // Then: Vérifier dans orders-dlq
        consumer = createConsumer("orders-dlq", "test-group-3");
        
        ConsumerRecord<String, Order> dlqRecord = pollForSpecificOrder(consumer, uniqueId, 20);

        assertThat(dlqRecord).isNotNull();
        assertThat(dlqRecord.value().getId()).isEqualTo(uniqueId);
        
        // Vérifier les headers d'erreur
        assertThat(dlqRecord.headers().lastHeader("kafka_dlt-exception-message"))
                .isNotNull();
    }

    // ==================== TEST 4: VALIDATION - MONTANT ====================

    @Test
    @DisplayName("TEST 4: Commande avec montant dépassant la limite → DLQ")
    void shouldRejectOrderExceedingAmountLimit() throws InterruptedException {
        // Given: Commande avec montant > 10000
        String uniqueId = "EXCEED-" + UUID.randomUUID().toString().substring(0, 8);
        Order exceedingOrder = new Order(
                uniqueId,
                "CUST-EXCEED-001",
                Arrays.asList("Expensive Item"),
                15000.00, // ❌ > 10000€
                "PENDING",
                System.currentTimeMillis()
        );

        // When
        orderProducerService.sendOrder(exceedingOrder);
        Thread.sleep(12000);

        // Then: Devrait être dans DLQ
        consumer = createConsumer("orders-dlq", "test-group-4");
        
        ConsumerRecord<String, Order> dlqRecord = pollForSpecificOrder(consumer, uniqueId, 20);

        assertThat(dlqRecord).isNotNull();
        assertThat(dlqRecord.value().getId()).isEqualTo(uniqueId);
    }

    // ==================== TEST 5: VALIDATION - CUSTOMER ID ====================

    @Test
    @DisplayName("TEST 5: Commande sans customerId → DLQ")
    void shouldRejectOrderWithoutCustomerId() throws InterruptedException {
        // Given
        String uniqueId = "NO-CUST-" + UUID.randomUUID().toString().substring(0, 8);
        Order noCustomerOrder = new Order(
                uniqueId,
                null, // ❌ Customer ID null
                Arrays.asList("Product"),
                100.00,
                "PENDING",
                System.currentTimeMillis()
        );

        // When
        orderProducerService.sendOrder(noCustomerOrder);
        Thread.sleep(12000);

        // Then
        consumer = createConsumer("orders-dlq", "test-group-5");
        
        ConsumerRecord<String, Order> dlqRecord = pollForSpecificOrder(consumer, uniqueId, 20);

        assertThat(dlqRecord).isNotNull();
        assertThat(dlqRecord.value().getId()).isEqualTo(uniqueId);
    }

    // ==================== TEST 6: PLUSIEURS COMMANDES ====================

    @Test
    @DisplayName("TEST 6: Traitement de plusieurs commandes valides")
    void shouldProcessMultipleValidOrders() throws InterruptedException {
        // Given: 3 commandes valides avec IDs uniques
        String id1 = "MULTI-1-" + UUID.randomUUID().toString().substring(0, 6);
        String id2 = "MULTI-2-" + UUID.randomUUID().toString().substring(0, 6);
        String id3 = "MULTI-3-" + UUID.randomUUID().toString().substring(0, 6);
        
        Order order1 = new Order(id1, "CUST-1", Arrays.asList("A"), 100.0, "PENDING", System.currentTimeMillis());
        Order order2 = new Order(id2, "CUST-2", Arrays.asList("B"), 200.0, "PENDING", System.currentTimeMillis());
        Order order3 = new Order(id3, "CUST-3", Arrays.asList("C"), 300.0, "PENDING", System.currentTimeMillis());

        // When: Envoyer toutes les commandes
        orderProducerService.sendOrder(order1);
        orderProducerService.sendOrder(order2);
        orderProducerService.sendOrder(order3);

        Thread.sleep(6000);

        // Then: Vérifier qu'au moins une a été traitée
        consumer = createConsumer("orders-processed", "test-group-6");
        
        // Lire plusieurs enregistrements
        int processedCount = 0;
        ConsumerRecords<String, Order> records = consumer.poll(Duration.ofSeconds(10));
        
        for (ConsumerRecord<String, Order> record : records) {
            if (record.value().getId().startsWith("MULTI-")) {
                assertThat(record.value().getStatus()).isEqualTo("COMPLETED");
                processedCount++;
            }
        }

        assertThat(processedCount).isGreaterThanOrEqualTo(1);
    }

    // ==================== TEST 7: CLÉ DU MESSAGE ====================

    @Test
    @DisplayName("TEST 7: La clé du message Kafka = ID de la commande")
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
        consumer = createConsumer("orders-input", "test-group-7");
        
        ConsumerRecord<String, Order> record = pollForSpecificOrder(consumer, uniqueId, 10);

        assertThat(record).isNotNull();
        assertThat(record.key()).isEqualTo(uniqueId);
        assertThat(record.key()).isEqualTo(record.value().getId());
    }

    // ==================== TEST 8: HAUTE VALEUR VALIDE ====================

    @Test
    @DisplayName("TEST 8: Commande de haute valeur (9999€) valide → Processed")
    void shouldProcessHighValueValidOrder() throws InterruptedException {
        // Given: Commande juste en dessous de la limite
        String uniqueId = "HIGH-VALUE-" + UUID.randomUUID().toString().substring(0, 8);
        Order highValueOrder = new Order(
                uniqueId,
                "VIP-CUST-001",
                Arrays.asList("Laptop", "Monitor", "Accessories"),
                9999.99, // ✅ < 10000
                "PENDING",
                System.currentTimeMillis()
        );

        // When
        orderProducerService.sendOrder(highValueOrder);
        Thread.sleep(5000);

        // Then: Devrait être traitée avec succès
        consumer = createConsumer("orders-processed", "test-group-8");
        
        ConsumerRecord<String, Order> processedRecord = pollForSpecificOrder(consumer, uniqueId, 15);

        assertThat(processedRecord).isNotNull();
        assertThat(processedRecord.value().getId()).isEqualTo(uniqueId);
        assertThat(processedRecord.value().getStatus()).isEqualTo("COMPLETED");
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
     * Évite de lire des messages d'autres tests
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
        
        return null; // Pas trouvé dans le timeout
    }
}