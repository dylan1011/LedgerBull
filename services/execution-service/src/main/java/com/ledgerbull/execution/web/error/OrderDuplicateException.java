package com.ledgerbull.execution.web.error;

public class OrderDuplicateException extends RuntimeException {

    public OrderDuplicateException(String message) {
        super(message);
    }
}
