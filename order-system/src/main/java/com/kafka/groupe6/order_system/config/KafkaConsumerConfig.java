package com.kafka.groupe6.order_system.config;

import com.kafka.groupe6.order_system.exception.OrderValidationException;
import com.kafka.groupe6.order_system.model.Order;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;
import org.springframework.kafka.support.serializer.JsonDeserializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuration Kafka Consumer avec gestion avancée des erreurs.
 * 
 * Responsable: EMANE (Tâche 4 - Parties B et C)
 * 
 * Fonctionnalités:
 * - DefaultErrorHandler avec retry (3 tentatives)
 * - Exponential backoff (1s, 2s, 4s)
 * - DeadLetterPublishingRecoverer vers 'orders-dlq'
 * - Headers d'erreur (exception, timestamp, retry-count)
 */
@Configuration
public class KafkaConsumerConfig {

    private static final Logger logger = LoggerFactory.getLogger(KafkaConsumerConfig.class);
    
    // Configuration du retry
    private static final int MAX_RETRIES = 3;
    private static final long INITIAL_INTERVAL_MS = 1000L;  // 1 seconde
    private static final double MULTIPLIER = 2.0;           // Backoff multiplier
    private static final long MAX_INTERVAL_MS = 10000L;     // 10 secondes max

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public KafkaConsumerConfig(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    @Bean
    public ConsumerFactory<String, Order> consumerFactory() {
        Map<String, Object> config = new HashMap<>();
        config.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092");
        config.put(ConsumerConfig.GROUP_ID_CONFIG, "order-consumer-group");
        config.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        config.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, JsonDeserializer.class);
        config.put(JsonDeserializer.TRUSTED_PACKAGES, "*");
        config.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        
        // Configuration supplémentaire pour la robustesse
        config.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        config.put(ConsumerConfig.MAX_POLL_RECORDS_CONFIG, 10);
        
        return new DefaultKafkaConsumerFactory<>(config, 
            new StringDeserializer(), 
            new JsonDeserializer<>(Order.class));
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> kafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Order> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        factory.setCommonErrorHandler(errorHandler());
        
        // Configuration supplémentaire
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        
        return factory;
    }

    /**
     * PARTIE B: Configuration du DefaultErrorHandler avec retry et exponential backoff.
     * PARTIE C: Configuration du DeadLetterPublishingRecoverer.
     */
    @Bean
    public CommonErrorHandler errorHandler() {
        // Configuration de l'exponential backoff
        ExponentialBackOffWithMaxRetries backOff = new ExponentialBackOffWithMaxRetries(MAX_RETRIES);
        backOff.setInitialInterval(INITIAL_INTERVAL_MS);
        backOff.setMultiplier(MULTIPLIER);
        backOff.setMaxInterval(MAX_INTERVAL_MS);
        
        // Création du DefaultErrorHandler avec DLQ recoverer
        DefaultErrorHandler errorHandler = new DefaultErrorHandler(
            deadLetterPublishingRecoverer(),
            backOff
        );
        
        // Configuration des exceptions non-retriables (pas de retry)
        errorHandler.addNotRetryableExceptions(OrderValidationException.class);
        
        // Logging des retries
        errorHandler.setRetryListeners((record, ex, deliveryAttempt) -> {
            logger.warn("⚠ Retry {} pour le message [topic={}, partition={}, offset={}]: {}",
                deliveryAttempt,
                record.topic(),
                record.partition(),
                record.offset(),
                ex.getMessage()
            );
        });
        
        logger.info("✓ DefaultErrorHandler configuré avec {} retries et exponential backoff", MAX_RETRIES);
        
        return errorHandler;
    }

    /**
     * PARTIE C: Configuration du DeadLetterPublishingRecoverer.
     * Route les messages en échec vers 'orders-dlq' avec headers d'erreur.
     */
    @Bean
    public DeadLetterPublishingRecoverer deadLetterPublishingRecoverer() {
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(
            kafkaTemplate,
            (record, exception) -> {
                // Route vers le topic DLQ
                logger.error("✗ Message envoyé vers DLQ: topic={}, partition={}, offset={}, exception={}",
                    record.topic(),
                    record.partition(),
                    record.offset(),
                    exception.getMessage()
                );
                return new TopicPartition(KafkaTopicConfig.ORDERS_DLQ_TOPIC, record.partition());
            }
        );
        
        // Ajouter des headers personnalisés pour le debugging
        recoverer.setHeadersFunction((consumerRecord, exception) -> {
            logger.debug("Ajout des headers d'erreur pour le message DLQ");
            return null; // Utilise les headers par défaut (inclut exception, timestamp, etc.)
        });
        
        logger.info("✓ DeadLetterPublishingRecoverer configuré vers '{}'", 
            KafkaTopicConfig.ORDERS_DLQ_TOPIC);
        
        return recoverer;
    }

    /**
     * Factory spécifique pour le consumer DLQ.
     * IMPORTANT: Pas d'error handler pour éviter une boucle infinie.
     */
    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, Order> dlqKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, Order> factory = 
            new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(consumerFactory());
        // Pas d'error handler - les messages DLQ sont loggés mais pas re-routés
        factory.setConcurrency(1);
        factory.getContainerProperties().setAckMode(
            org.springframework.kafka.listener.ContainerProperties.AckMode.RECORD
        );
        
        logger.info("✓ DLQ KafkaListenerContainerFactory configuré (sans error handler)");
        
        return factory;
    }
}
