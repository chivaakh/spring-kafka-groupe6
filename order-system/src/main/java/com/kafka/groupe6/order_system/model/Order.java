package com.kafka.groupe6.order_system.model;

import java.util.List;

public class Order {
    private String id;
    private String customerId;
    private List<String> items;
    private double totalAmount;
    private String status;
    private long timestamp;

    // Constructeurs
    public Order() {}

    public Order(String id, String customerId, List<String> items, 
                 double totalAmount, String status, long timestamp) {
        this.id = id;
        this.customerId = customerId;
        this.items = items;
        this.totalAmount = totalAmount;
        this.status = status;
        this.timestamp = timestamp;
    }

    // Getters et Setters
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    
    public List<String> getItems() { return items; }
    public void setItems(List<String> items) { this.items = items; }
    
    public double getTotalAmount() { return totalAmount; }
    public void setTotalAmount(double totalAmount) { this.totalAmount = totalAmount; }
    
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    
    public long getTimestamp() { return timestamp; }
    public void setTimestamp(long timestamp) { this.timestamp = timestamp; }
}