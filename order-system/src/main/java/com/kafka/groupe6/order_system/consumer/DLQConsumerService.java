package com.kafka.groupe6.order_system.consumer;

import com.kafka.groupe6.order_system.config.KafkaTopicConfig;
import com.kafka.groupe6.order_system.model.Order;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;

/**
 * Service de consommation des messages de la Dead Letter Queue.
 * 
 * Responsable: EMANE (Tâche 4 - Partie C)
 * 
 * Ce service:
 * - Écoute le topic 'orders-dlq'
 * - Log les informations détaillées sur les erreurs
 * - Peut être étendu pour stocker en base, alerter, etc.
 */
@Service
public class DLQConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(DLQConsumerService.class);
    
    // Headers DLQ standards de Spring Kafka
    private static final String DLT_EXCEPTION_FQCN = "kafka_dlt-exception-fqcn";
    private static final String DLT_EXCEPTION_MESSAGE = "kafka_dlt-exception-message";
    private static final String DLT_ORIGINAL_TOPIC = "kafka_dlt-original-topic";
    private static final String DLT_ORIGINAL_PARTITION = "kafka_dlt-original-partition";
    private static final String DLT_ORIGINAL_OFFSET = "kafka_dlt-original-offset";
    private static final String DLT_ORIGINAL_TIMESTAMP = "kafka_dlt-original-timestamp";

    @KafkaListener(
        topics = KafkaTopicConfig.ORDERS_DLQ_TOPIC,
        groupId = "dlq-consumer-group",
        containerFactory = "dlqKafkaListenerContainerFactory"
    )
    public void consumeDLQMessage(ConsumerRecord<String, Order> record) {
        
        logger.error("╔══════════════════════════════════════════════════════════════╗");
        logger.error("║           MESSAGE REÇU DANS LA DEAD LETTER QUEUE             ║");
        logger.error("╠══════════════════════════════════════════════════════════════╣");
        
        // Informations sur le message DLQ
        logger.error("║ DLQ Topic: {}", record.topic());
        logger.error("║ DLQ Partition: {}", record.partition());
        logger.error("║ DLQ Offset: {}", record.offset());
        logger.error("║ DLQ Timestamp: {}", record.timestamp());
        logger.error("╠══════════════════════════════════════════════════════════════╣");
        
        // Extraction des headers d'erreur
        Headers headers = record.headers();
        
        String exceptionClass = getHeaderValue(headers, DLT_EXCEPTION_FQCN);
        String exceptionMessage = getHeaderValue(headers, DLT_EXCEPTION_MESSAGE);
        String originalTopic = getHeaderValue(headers, DLT_ORIGINAL_TOPIC);
        String originalPartition = getHeaderValue(headers, DLT_ORIGINAL_PARTITION);
        String originalOffset = getHeaderValue(headers, DLT_ORIGINAL_OFFSET);
        String originalTimestamp = getHeaderValue(headers, DLT_ORIGINAL_TIMESTAMP);
        
        logger.error("║ INFORMATIONS D'ERREUR:");
        logger.error("║   Exception: {}", exceptionClass);
        logger.error("║   Message: {}", exceptionMessage);
        logger.error("╠══════════════════════════════════════════════════════════════╣");
        
        logger.error("║ INFORMATIONS ORIGINALES:");
        logger.error("║   Topic original: {}", originalTopic);
        logger.error("║   Partition originale: {}", originalPartition);
        logger.error("║   Offset original: {}", originalOffset);
        logger.error("║   Timestamp original: {}", originalTimestamp);
        logger.error("╠══════════════════════════════════════════════════════════════╣");
        
        // Données de la commande
        Order order = record.value();
        if (order != null) {
            logger.error("║ DONNÉES DE LA COMMANDE:");
            logger.error("║   Order ID: {}", order.getId());
            logger.error("║   Customer ID: {}", order.getCustomerId());
            logger.error("║   Items: {}", order.getItems());
            logger.error("║   Total Amount: {}€", order.getTotalAmount());
            logger.error("║   Status: {}", order.getStatus());
        } else {
            logger.error("║ DONNÉES: Commande null ou non désérialisable");
        }
        
        logger.error("╚══════════════════════════════════════════════════════════════╝");
        
        // Ici, on pourrait ajouter:
        // - Stockage en base de données pour analyse
        // - Envoi d'alertes (email, Slack, etc.)
        // - Métriques pour monitoring
        // - Retry manuel après correction
        
        handleDLQMessage(order, exceptionClass, exceptionMessage);
    }

    /**
     * Traitement personnalisé des messages DLQ.
     * Peut être étendu selon les besoins métier.
     */
    private void handleDLQMessage(Order order, String exceptionClass, String exceptionMessage) {
        if (order == null) {
            logger.warn("Impossible de traiter le message DLQ: commande null");
            return;
        }
        
        // Analyse du type d'erreur
        if (exceptionClass != null) {
            if (exceptionClass.contains("OrderValidationException")) {
                logger.info("→ Erreur de validation détectée. Action: révision manuelle requise.");
                // Pourrait déclencher une alerte au support
                
            } else if (exceptionClass.contains("StockUnavailableException")) {
                logger.info("→ Erreur de stock détectée. Action: vérifier l'inventaire.");
                // Pourrait programmer un retry automatique plus tard
                
            } else if (exceptionClass.contains("DeserializationException")) {
                logger.info("→ Erreur de désérialisation. Action: vérifier le format du message.");
                // Format de message invalide
                
            } else {
                logger.info("→ Erreur inconnue. Action: investigation requise.");
            }
        }
        
        // Log pour les métriques/monitoring
        logger.info("DLQ_METRIC: orderId={}, exception={}", 
            order.getId(), 
            exceptionClass != null ? exceptionClass : "unknown"
        );
    }

    /**
     * Extrait la valeur d'un header Kafka.
     */
    private String getHeaderValue(Headers headers, String headerName) {
        Header header = headers.lastHeader(headerName);
        if (header != null && header.value() != null) {
            return new String(header.value(), StandardCharsets.UTF_8);
        }
        return "N/A";
    }
}

