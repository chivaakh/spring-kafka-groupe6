package com.kafka.groupe6.order_system.integration;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.Consumer;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import static org.assertj.core.api.Assertions.assertThat;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.kafka.test.EmbeddedKafkaBroker;
import org.springframework.kafka.test.context.EmbeddedKafka;
import org.springframework.kafka.test.utils.KafkaTestUtils;

import com.kafka.groupe6.order_system.model.Order;

@SpringBootTest
@EmbeddedKafka(topics = { "orders-input", "orders-processed", "orders-dlq" }, partitions = 1)
@org.springframework.test.context.TestPropertySource(properties = {
    "spring.kafka.bootstrap-servers=${spring.embedded.kafka.brokers}",
    "app.stock.simulate-failures=false"
})
@org.springframework.test.annotation.DirtiesContext(classMode = org.springframework.test.annotation.DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
@Disabled("Désactivé car Docker Kafka tourne - utiliser quand Docker est arrêté")
class KafkaIntegrationTest {

    @Autowired
    private EmbeddedKafkaBroker embeddedKafkaBroker;

    @Test
    void shouldSendAndReceiveOrderThroughKafka() {

        // 1️⃣ Créer une commande
        Order order = new Order(
                "100",
                "C100",
                List.of("Item1"),
                99.0,
                "PENDING",
                System.currentTimeMillis()
        );

        // 2️⃣ Créer un Producer Kafka NATIF (lié à EmbeddedKafka)
        Map<String, Object> producerProps =
                KafkaTestUtils.producerProps(embeddedKafkaBroker);

        KafkaProducer<String, Order> producer = new KafkaProducer<>(
                producerProps,
                new StringSerializer(),
                new JsonSerializer<>()
        );

        producer.send(new ProducerRecord<>("orders-input", order.getId(), order));
        producer.flush();

        // 3️⃣ Créer un Consumer Kafka NATIF
        Map<String, Object> consumerProps =
                KafkaTestUtils.consumerProps("test-group", "true", embeddedKafkaBroker);

        consumerProps.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        consumerProps.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        consumerProps.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        consumerProps.put(JsonDeserializer.TRUSTED_PACKAGES, "*");

        Consumer<String, Order> consumer = new KafkaConsumer<>(consumerProps);
        embeddedKafkaBroker.consumeFromAnEmbeddedTopic(consumer, "orders-input");

        // 4️⃣ Lire le message
        ConsumerRecord<String, Order> record =
                KafkaTestUtils.getSingleRecord(
                        consumer,
                        "orders-input",
                        Duration.ofSeconds(5)
                );

        // 5️⃣ Vérifier
        assertThat(record).isNotNull();
        assertThat(record.value().getId()).isEqualTo(order.getId());
        assertThat(record.value().getCustomerId()).isEqualTo(order.getCustomerId());

        producer.close();
        consumer.close();
    }
}
