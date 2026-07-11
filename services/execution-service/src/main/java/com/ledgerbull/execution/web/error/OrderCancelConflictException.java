package com.ledgerbull.execution.web.error;

public class OrderCancelConflictException extends RuntimeException {

    public OrderCancelConflictException(String message) {
        super(message);
    }
}
