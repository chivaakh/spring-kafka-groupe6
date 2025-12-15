package com.kafka.groupe6.order_system.exception;

/**
 * Exception levée lorsque le stock n'est pas disponible.
 * Cette exception est retriable (on peut réessayer plus tard).
 */
public class StockUnavailableException extends RuntimeException {
    
    private final String orderId;
    private final String item;

    public StockUnavailableException(String orderId, String item) {
        super("Stock unavailable for order " + orderId + ", item: " + item);
        this.orderId = orderId;
        this.item = item;
    }

    public StockUnavailableException(String orderId, String item, Throwable cause) {
        super("Stock unavailable for order " + orderId + ", item: " + item, cause);
        this.orderId = orderId;
        this.item = item;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getItem() {
        return item;
    }
}

