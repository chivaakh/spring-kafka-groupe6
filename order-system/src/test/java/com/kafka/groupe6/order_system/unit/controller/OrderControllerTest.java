package com.kafka.groupe6.order_system.unit.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.kafka.groupe6.order_system.controller.OrderController;
import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;

import java.util.List;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private OrderProducerService orderProducerService;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    void shouldSendOrderWhenPostRequestIsValid() throws Exception {
        // Given : une commande valide
        Order order = new Order(
                "1",
                "C1",
                List.of("Item1"),
                50.0,
                "PENDING",
                System.currentTimeMillis()
        );

        doNothing().when(orderProducerService).sendOrder(any(Order.class));

        // When & Then : appel POST vers l’API
        mockMvc.perform(post("/api/orders")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(order)))
                .andExpect(status().isOk());
    }
}
