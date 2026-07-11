package com.ledgerbull.execution.web.error;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ExecutionExceptionHandler {

    @ExceptionHandler(OrderValidationException.class)
    public ResponseEntity<ErrorResponse> handleValidation(OrderValidationException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EngineUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEngineUnavailable(EngineUnavailableException ex) {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                .body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(EngineRejectedException.class)
    public ResponseEntity<ErrorResponse> handleEngineRejected(EngineRejectedException ex) {
        return ResponseEntity.badRequest().body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(OrderNotFoundException.class)
    public ResponseEntity<ErrorResponse> handleOrderNotFound(OrderNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(OrderCancelConflictException.class)
    public ResponseEntity<ErrorResponse> handleOrderCancelConflict(OrderCancelConflictException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    @ExceptionHandler(OrderDuplicateException.class)
    public ResponseEntity<ErrorResponse> handleOrderDuplicate(OrderDuplicateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT).body(new ErrorResponse(ex.getMessage()));
    }

    public record ErrorResponse(String error) {
    }
}
