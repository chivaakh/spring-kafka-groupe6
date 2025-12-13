package com.kafka.groupe6.order_system.controller;

import com.kafka.groupe6.order_system.model.Order;
import com.kafka.groupe6.order_system.producer.OrderProducerService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    // Injection manuelle du service
    private final OrderProducerService producerService;

    // Constructeur pour l'injection de dépendance
    public OrderController(OrderProducerService producerService) {
        this.producerService = producerService;
    }

    @PostMapping
    public String sendOrder(@RequestBody Order order) {
        order.setTimestamp(System.currentTimeMillis());
        producerService.sendOrder(order);
        return "Order sent!";
    }

    @GetMapping("/generate")
    public String generate() {
        Order order = new Order(
                UUID.randomUUID().toString(),
                "CUST-" + (int)(Math.random()*1000),
                List.of("Item1", "Item2"),
                Math.random() * 200,
                "PENDING",
                System.currentTimeMillis()
        );

        producerService.sendOrder(order);
        return "Random order generated and sent!";
    }
}