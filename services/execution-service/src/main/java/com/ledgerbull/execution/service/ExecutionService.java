package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.MatchingEngineClient;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.OrderRepository;
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
    private final OrderRepository orderRepository;

    public ExecutionService(
            OrderValidationService validationService,
            MatchingEngineClient engineClient,
            OrderRepository orderRepository) {
        this.validationService = validationService;
        this.engineClient = engineClient;
        this.orderRepository = orderRepository;
    }

    public SubmitOrderResponse submitOrder(OrderRequest request) {
        OrderValidationService.ValidatedOrder validated = validationService.validate(request);
        saveNewOrder(validated);
        return engineClient.submitOrder(
                validated.orderId(),
                validated.symbol(),
                validated.side(),
                validated.type(),
                validated.priceTicks(),
                validated.quantity());
    }

    private void saveNewOrder(OrderValidationService.ValidatedOrder validated) {
        OrderEntity entity = new OrderEntity();
        entity.setOrderId(validated.orderId());
        entity.setSymbol(validated.symbol());
        entity.setSide(validated.side());
        entity.setOrderType(validated.type());
        entity.setPrice(validated.type().equals("LIMIT") ? validated.priceTicks() : null);
        entity.setOriginalQuantity(validated.quantity());
        entity.setFilledQuantity(0L);
        entity.setRemainingQuantity(validated.quantity());
        entity.setStatus("NEW");
        orderRepository.save(entity);
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
