package com.kafka.groupe6.order_system.consumer;

import com.kafka.groupe6.order_system.config.KafkaTopicConfig;
import com.kafka.groupe6.order_system.exception.OrderValidationException;
import com.kafka.groupe6.order_system.exception.StockUnavailableException;
import com.kafka.groupe6.order_system.model.Order;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Service;

import java.util.Random;

import org.springframework.beans.factory.annotation.Value;

/**
 * Service de consommation et traitement des commandes.
 * 
 * Responsable: WETHIGHA (Tâche 4)
 * 
 * Fonctionnalités:
 * - Réception des messages du topic 'orders-input'
 * - Validation métier des commandes
 * - Simulation de vérification du stock
 * - Changement de statut: PENDING → PROCESSING → COMPLETED
 * - Publication vers 'orders-processed'
 */
@Service
public class OrderConsumerService {

    private static final Logger logger = LoggerFactory.getLogger(OrderConsumerService.class);
    
    // Constantes pour la validation
    private static final double MIN_ORDER_AMOUNT = 0.01;
    private static final double MAX_ORDER_AMOUNT = 10000.00;
    
    // Statuts des commandes
    private static final String STATUS_PENDING = "PENDING";
    private static final String STATUS_PROCESSING = "PROCESSING";
    private static final String STATUS_COMPLETED = "COMPLETED";
    private static final String STATUS_FAILED = "FAILED";
    
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Random random = new Random();
    
    // Désactive le comportement aléatoire du stock pour les tests
    @Value("${app.stock.simulate-failures:true}")
    private boolean simulateStockFailures;

    @org.springframework.beans.factory.annotation.Autowired
    public OrderConsumerService(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
        this.simulateStockFailures = true;
    }
    
    // Constructeur pour les tests (permet de désactiver les échecs aléatoires)
    public OrderConsumerService(KafkaTemplate<String, Object> kafkaTemplate, boolean simulateStockFailures) {
        this.kafkaTemplate = kafkaTemplate;
        this.simulateStockFailures = simulateStockFailures;
    }

    @KafkaListener(
        topics = KafkaTopicConfig.ORDERS_INPUT_TOPIC,
        groupId = "order-consumer-group",
        containerFactory = "kafkaListenerContainerFactory"
    )
    public void consumeOrder(
            @Payload Order order,
            @Header(KafkaHeaders.RECEIVED_PARTITION) int partition,
            @Header(KafkaHeaders.OFFSET) long offset,
            @Header(KafkaHeaders.RECEIVED_TIMESTAMP) long timestamp) {
        
        logger.info("========================================");
        logger.info("Message reçu du topic 'orders-input'");
        logger.info("Partition: {}, Offset: {}", partition, offset);
        logger.info("Order ID: {}, Customer: {}", order.getId(), order.getCustomerId());
        logger.info("Items: {}, Amount: {}", order.getItems(), order.getTotalAmount());
        logger.info("Status initial: {}", order.getStatus());
        logger.info("========================================");

        try {
            // PARTIE A: Logique de traitement
            
            // 1. Validation de la commande
            validateOrder(order);
            logger.info("✓ Validation réussie pour la commande {}", order.getId());
            
            // 2. Changement de statut: PENDING → PROCESSING
            order.setStatus(STATUS_PROCESSING);
            logger.info("→ Statut changé en PROCESSING pour la commande {}", order.getId());
            
            // 3. Simulation de vérification du stock
            checkStock(order);
            logger.info("✓ Stock vérifié pour la commande {}", order.getId());
            
            // 4. Simulation du traitement (processing)
            processOrder(order);
            
            // 5. Changement de statut: PROCESSING → COMPLETED
            order.setStatus(STATUS_COMPLETED);
            order.setTimestamp(System.currentTimeMillis());
            logger.info("✓ Statut changé en COMPLETED pour la commande {}", order.getId());
            
            // 6. Publication vers 'orders-processed'
            publishProcessedOrder(order);
            
            logger.info("========================================");
            logger.info("✓✓ Commande {} traitée avec succès!", order.getId());
            logger.info("========================================");
            
        } catch (OrderValidationException e) {
            // Erreur de validation - non retriable
            logger.error("✗ Erreur de validation pour la commande {}: {}", 
                order.getId(), e.getValidationError());
            order.setStatus(STATUS_FAILED);
            throw e; // Sera capturé par le DefaultErrorHandler
            
        } catch (StockUnavailableException e) {
            // Stock indisponible - retriable
            logger.warn("⚠ Stock indisponible pour la commande {}: {}", 
                order.getId(), e.getMessage());
            throw e; // Sera reessayé par le DefaultErrorHandler
            
        } catch (Exception e) {
            // Autres erreurs
            logger.error("✗ Erreur inattendue lors du traitement de la commande {}: {}", 
                order.getId(), e.getMessage(), e);
            order.setStatus(STATUS_FAILED);
            throw e;
        }
    }

    /**
     * Valide les données de la commande.
     * Vérifie: montant, ID, customer ID, items
     */
    private void validateOrder(Order order) {
        logger.debug("Validation de la commande {}", order.getId());
        
        // Vérification de l'ID
        if (order.getId() == null || order.getId().trim().isEmpty()) {
            throw new OrderValidationException(
                order.getId(), 
                "L'ID de la commande est obligatoire"
            );
        }
        
        // Vérification du customer ID
        if (order.getCustomerId() == null || order.getCustomerId().trim().isEmpty()) {
            throw new OrderValidationException(
                order.getId(), 
                "L'ID du client est obligatoire"
            );
        }
        
        // Vérification des items
        if (order.getItems() == null || order.getItems().isEmpty()) {
            throw new OrderValidationException(
                order.getId(), 
                "La commande doit contenir au moins un article"
            );
        }
        
        // Vérification du montant
        if (order.getTotalAmount() < MIN_ORDER_AMOUNT) {
            throw new OrderValidationException(
                order.getId(), 
                String.format("Le montant minimum est de %.2f€", MIN_ORDER_AMOUNT)
            );
        }
        
        if (order.getTotalAmount() > MAX_ORDER_AMOUNT) {
            throw new OrderValidationException(
                order.getId(), 
                String.format("Le montant maximum est de %.2f€", MAX_ORDER_AMOUNT)
            );
        }
    }

    /**
     * Simule la vérification du stock.
     * Génère aléatoirement une erreur de stock (10% de chance) pour tester le retry.
     * Ce comportement peut être désactivé via la propriété app.stock.simulate-failures=false
     */
    private void checkStock(Order order) {
        logger.debug("Vérification du stock pour la commande {}", order.getId());
        
        // Simulation: 10% de chance d'échec de stock (pour tester le retry)
        // Désactivé si simulateStockFailures = false (pour les tests)
        if (simulateStockFailures && random.nextInt(100) < 10) {
            String item = order.getItems().get(0);
            throw new StockUnavailableException(order.getId(), item);
        }
        
        // Simulation du temps de vérification
        simulateProcessingDelay(50, 150);
        
        logger.debug("Stock disponible pour tous les articles de la commande {}", order.getId());
    }

    /**
     * Simule le traitement de la commande.
     */
    private void processOrder(Order order) {
        logger.debug("Traitement de la commande {}", order.getId());
        
        // Simulation du temps de traitement
        simulateProcessingDelay(100, 300);
        
        logger.debug("Traitement terminé pour la commande {}", order.getId());
    }

    /**
     * Publie la commande traitée vers le topic 'orders-processed'.
     */
    private void publishProcessedOrder(Order order) {
        logger.info("Publication vers '{}' pour la commande {}", 
            KafkaTopicConfig.ORDERS_PROCESSED_TOPIC, order.getId());
        
        kafkaTemplate.send(
            KafkaTopicConfig.ORDERS_PROCESSED_TOPIC, 
            order.getId(), 
            order
        ).whenComplete((result, ex) -> {
            if (ex == null) {
                logger.info("✓ Commande {} publiée vers 'orders-processed'", order.getId());
                logger.debug("Topic: {}, Partition: {}, Offset: {}", 
                    result.getRecordMetadata().topic(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            } else {
                logger.error("✗ Échec de publication pour la commande {}: {}", 
                    order.getId(), ex.getMessage());
            }
        });
    }

    /**
     * Simule un délai de traitement aléatoire.
     */
    private void simulateProcessingDelay(int minMs, int maxMs) {
        try {
            Thread.sleep(random.nextInt(maxMs - minMs) + minMs);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
