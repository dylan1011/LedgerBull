package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.EngineFill;
import com.ledgerbull.execution.client.EngineSubmitResult;
import com.ledgerbull.execution.client.MatchingEngineClient;
import com.ledgerbull.execution.entity.FillEntity;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.FillRepository;
import com.ledgerbull.execution.repository.OrderRepository;
import com.ledgerbull.execution.web.error.EngineRejectedException;
import com.ledgerbull.execution.web.error.EngineUnavailableException;
import com.ledgerbull.execution.web.error.OrderValidationException;
import com.ledgerbull.execution.web.dto.BookResponse;
import com.ledgerbull.execution.web.dto.CancelOrderResponse;
import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ExecutionService {

    private static final Logger log = LoggerFactory.getLogger(ExecutionService.class);

    private final OrderValidationService validationService;
    private final MatchingEngineClient engineClient;
    private final OrderRepository orderRepository;
    private final FillRepository fillRepository;
    private final OrderStateService orderStateService;

    public ExecutionService(
            OrderValidationService validationService,
            MatchingEngineClient engineClient,
            OrderRepository orderRepository,
            FillRepository fillRepository,
            OrderStateService orderStateService) {
        this.validationService = validationService;
        this.engineClient = engineClient;
        this.orderRepository = orderRepository;
        this.fillRepository = fillRepository;
        this.orderStateService = orderStateService;
    }

    public SubmitOrderResponse submitOrder(OrderRequest request) {
        OrderValidationService.ValidatedOrder validated = validationService.validate(request);
        OrderEntity savedOrder = saveNewOrder(validated);
        try {
            EngineSubmitResult result = engineClient.submitOrder(
                    validated.orderId(),
                    validated.symbol(),
                    validated.side(),
                    validated.type(),
                    validated.priceTicks(),
                    validated.quantity());
            if (!result.response().accepted()) {
                String reason = result.response().reject_reason();
                markOrderRejected(savedOrder, reason);
                throw new EngineRejectedException(
                        reason != null && !reason.isBlank() ? reason : "order rejected by matching engine");
            }
            saveFills(savedOrder.getId(), result.fills());
            orderStateService.applyFillsToSubmittingOrder(savedOrder, result.fills());
            orderStateService.applyFillsToMakerOrders(result.fills());
            return result.response();
        } catch (EngineUnavailableException ex) {
            markOrderRejected(savedOrder, ex.getMessage());
            throw ex;
        }
    }

    private OrderEntity saveNewOrder(OrderValidationService.ValidatedOrder validated) {
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
        return orderRepository.save(entity);
    }

    private void markOrderRejected(OrderEntity order, String reason) {
        if (!"NEW".equals(order.getStatus())) {
            return;
        }
        order.setStatus("REJECTED");
        orderRepository.save(order);
        if (reason != null && !reason.isBlank()) {
            log.info("Order {} rejected: {}", order.getOrderId(), reason);
        }
    }

    private void saveFills(Long orderRefId, List<EngineFill> fills) {
        for (EngineFill fill : fills) {
            FillEntity entity = new FillEntity();
            entity.setOrderRefId(orderRefId);
            entity.setTakerOrderId(fill.takerOrderId());
            entity.setMakerOrderId(fill.makerOrderId());
            entity.setSymbol(fill.symbol());
            entity.setFillPrice(fill.priceTicks());
            entity.setFillQuantity(fill.quantity());
            fillRepository.save(entity);
        }
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
