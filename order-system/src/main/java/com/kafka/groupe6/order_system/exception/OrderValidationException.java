package com.kafka.groupe6.order_system.exception;

/**
 * Exception levée lors de la validation d'une commande.
 * Cette exception est non-retriable (pas de retry).
 */
public class OrderValidationException extends RuntimeException {
    
    private final String orderId;
    private final String validationError;

    public OrderValidationException(String orderId, String validationError) {
        super("Validation failed for order " + orderId + ": " + validationError);
        this.orderId = orderId;
        this.validationError = validationError;
    }

    public OrderValidationException(String orderId, String validationError, Throwable cause) {
        super("Validation failed for order " + orderId + ": " + validationError, cause);
        this.orderId = orderId;
        this.validationError = validationError;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getValidationError() {
        return validationError;
    }
}

