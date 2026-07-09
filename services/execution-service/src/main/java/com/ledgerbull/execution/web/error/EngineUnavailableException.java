package com.ledgerbull.execution.web.error;

public class EngineUnavailableException extends RuntimeException {

    public EngineUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
