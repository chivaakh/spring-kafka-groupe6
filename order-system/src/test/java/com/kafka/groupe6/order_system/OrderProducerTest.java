package com.kafka.groupe6.order_system;

import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OrderProducerTest {

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderProducerService service;

    @Test
    void testSendOrder() {
        // Given
        Order order = new Order("1", "C1", List.of("Item"), 20.0, "PENDING", System.currentTimeMillis());
        
        // Créer un mock de SendResult
        SendResult<String, Object> sendResult = mock(SendResult.class);
        
        // Créer un CompletableFuture avec le bon type générique
        CompletableFuture<SendResult<String, Object>> future = 
            CompletableFuture.completedFuture(sendResult);
        
        when(kafkaTemplate.send(eq("orders-input"), eq("1"), eq(order)))
                .thenReturn(future);

        // When
        service.sendOrder(order);

        // Then
        verify(kafkaTemplate, times(1))
                .send("orders-input", "1", order);
    }
}