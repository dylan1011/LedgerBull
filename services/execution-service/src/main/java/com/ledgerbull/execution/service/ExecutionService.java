package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.MatchingEngineClient;
import com.ledgerbull.execution.web.error.OrderValidationException;
import com.ledgerbull.execution.web.dto.BookResponse;
import com.ledgerbull.execution.web.dto.CancelOrderResponse;
import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {

    private final OrderValidationService validationService;
    private final MatchingEngineClient engineClient;

    public ExecutionService(OrderValidationService validationService, MatchingEngineClient engineClient) {
        this.validationService = validationService;
        this.engineClient = engineClient;
    }

    public SubmitOrderResponse submitOrder(OrderRequest request) {
        OrderValidationService.ValidatedOrder validated = validationService.validate(request);
        return engineClient.submitOrder(
                validated.orderId(),
                validated.symbol(),
                validated.side(),
                validated.type(),
                validated.priceTicks(),
                validated.quantity());
    }

    public CancelOrderResponse cancelOrder(String orderId) {
        validationService.validateCancelOrderId(orderId);
        return engineClient.cancelOrder(orderId);
    }

    public BookResponse queryBook(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            throw new OrderValidationException("symbol is required");
        }
        return engineClient.queryBook(symbol.trim());
    }
}
