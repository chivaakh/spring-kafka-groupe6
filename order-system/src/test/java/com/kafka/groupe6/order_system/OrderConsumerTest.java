package com.kafka.groupe6.order_system;

import com.kafka.groupe6.order_system.consumer.OrderConsumerService;
import com.kafka.groupe6.order_system.exception.OrderValidationException;
import com.kafka.groupe6.order_system.model.Order;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.Arrays;
import java.util.Collections;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Tests unitaires pour OrderConsumerService.
 * 
 * Responsable: EMANE (Tâche 4 - Partie D)
 * 
 * Tests couverts:
 * - Traitement réussi d'une commande valide
 * - Validation des commandes (montant, items, IDs)
 * - Publication vers 'orders-processed'
 * - Gestion des erreurs
 */
@ExtendWith(MockitoExtension.class)
class OrderConsumerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderConsumerService consumerService;

    @BeforeEach
    void setUp() {
        consumerService = new OrderConsumerService(kafkaTemplate);
    }

    // ==================== TESTS DE TRAITEMENT RÉUSSI ====================

    @Test
    @DisplayName("Doit traiter une commande valide avec succès")
    void shouldProcessValidOrderSuccessfully() {
        // Given
        Order order = createValidOrder();
        mockKafkaTemplateSend();

        // When & Then - Pas d'exception
        assertDoesNotThrow(() -> 
            consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        // Vérifier que la commande a été publiée vers 'orders-processed'
        verify(kafkaTemplate, times(1))
            .send(eq("orders-processed"), eq(order.getId()), any(Order.class));
    }

    @Test
    @DisplayName("Doit changer le statut de PENDING à COMPLETED")
    void shouldChangeStatusFromPendingToCompleted() {
        // Given
        Order order = createValidOrder();
        order.setStatus("PENDING");
        mockKafkaTemplateSend();

        // When
        consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis());

        // Then
        assertEquals("COMPLETED", order.getStatus());
    }

    // ==================== TESTS DE VALIDATION ====================

    @Test
    @DisplayName("Doit rejeter une commande sans ID")
    void shouldRejectOrderWithoutId() {
        // Given
        Order order = createValidOrder();
        order.setId(null);

        // When & Then
        OrderValidationException exception = assertThrows(
            OrderValidationException.class,
            () -> consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        assertTrue(exception.getMessage().contains("obligatoire"));
    }

    @Test
    @DisplayName("Doit rejeter une commande sans customer ID")
    void shouldRejectOrderWithoutCustomerId() {
        // Given
        Order order = createValidOrder();
        order.setCustomerId(null);

        // When & Then
        OrderValidationException exception = assertThrows(
            OrderValidationException.class,
            () -> consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        assertTrue(exception.getMessage().contains("client"));
    }

    @Test
    @DisplayName("Doit rejeter une commande sans items")
    void shouldRejectOrderWithoutItems() {
        // Given
        Order order = createValidOrder();
        order.setItems(Collections.emptyList());

        // When & Then
        OrderValidationException exception = assertThrows(
            OrderValidationException.class,
            () -> consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        assertTrue(exception.getMessage().contains("article"));
    }

    @Test
    @DisplayName("Doit rejeter une commande avec montant négatif")
    void shouldRejectOrderWithNegativeAmount() {
        // Given
        Order order = createValidOrder();
        order.setTotalAmount(-10.0);

        // When & Then
        OrderValidationException exception = assertThrows(
            OrderValidationException.class,
            () -> consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        assertTrue(exception.getMessage().contains("minimum"));
    }

    @Test
    @DisplayName("Doit rejeter une commande avec montant trop élevé")
    void shouldRejectOrderWithExcessiveAmount() {
        // Given
        Order order = createValidOrder();
        order.setTotalAmount(15000.0); // > 10000€ max

        // When & Then
        OrderValidationException exception = assertThrows(
            OrderValidationException.class,
            () -> consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );

        assertTrue(exception.getMessage().contains("maximum"));
    }

    @Test
    @DisplayName("Doit accepter une commande au montant limite minimum")
    void shouldAcceptOrderWithMinimumAmount() {
        // Given
        Order order = createValidOrder();
        order.setTotalAmount(0.01); // Montant minimum
        mockKafkaTemplateSend();

        // When & Then
        assertDoesNotThrow(() -> 
            consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );
    }

    @Test
    @DisplayName("Doit accepter une commande au montant limite maximum")
    void shouldAcceptOrderWithMaximumAmount() {
        // Given
        Order order = createValidOrder();
        order.setTotalAmount(10000.0); // Montant maximum
        mockKafkaTemplateSend();

        // When & Then
        assertDoesNotThrow(() -> 
            consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis())
        );
    }

    // ==================== TESTS DE PUBLICATION ====================

    @Test
    @DisplayName("Doit publier vers le topic orders-processed")
    void shouldPublishToOrdersProcessedTopic() {
        // Given
        Order order = createValidOrder();
        mockKafkaTemplateSend();

        // When
        consumerService.consumeOrder(order, 0, 0L, System.currentTimeMillis());

        // Then
        verify(kafkaTemplate).send(
            eq("orders-processed"),
            eq(order.getId()),
            argThat(o -> o instanceof Order && "COMPLETED".equals(((Order) o).getStatus()))
        );
    }

    // ==================== HELPERS ====================

    private Order createValidOrder() {
        return new Order(
            "ORDER-001",
            "CUSTOMER-001",
            Arrays.asList("Laptop", "Mouse", "Keyboard"),
            299.99,
            "PENDING",
            System.currentTimeMillis()
        );
    }

    private void mockKafkaTemplateSend() {
        SendResult<String, Object> sendResult = mock(SendResult.class);
        org.apache.kafka.clients.producer.RecordMetadata metadata = mock(
            org.apache.kafka.clients.producer.RecordMetadata.class
        );
        when(metadata.topic()).thenReturn("orders-processed");
        when(metadata.partition()).thenReturn(0);
        when(metadata.offset()).thenReturn(0L);
        when(sendResult.getRecordMetadata()).thenReturn(metadata);
        
        CompletableFuture<SendResult<String, Object>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(anyString(), anyString(), any()))
            .thenReturn(future);
    }
}

