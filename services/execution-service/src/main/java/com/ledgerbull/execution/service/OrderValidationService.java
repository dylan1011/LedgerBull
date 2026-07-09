package com.ledgerbull.execution.service;

import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.error.OrderValidationException;
import java.util.Locale;
import org.springframework.stereotype.Service;

@Service
public class OrderValidationService {

    public ValidatedOrder validate(OrderRequest request) {
        if (request.order_id() == null || request.order_id().isBlank()) {
            throw new OrderValidationException("order_id is required");
        }
        if (!request.order_id().matches("\\d+")) {
            throw new OrderValidationException("order_id must be numeric");
        }
        if (request.order_id().equals("0")) {
            throw new OrderValidationException("order_id must be non-zero");
        }

        if (request.symbol() == null || request.symbol().isBlank()) {
            throw new OrderValidationException("symbol is required");
        }

        if (request.side() == null) {
            throw new OrderValidationException("side is required");
        }
        String side = request.side().trim().toUpperCase(Locale.ROOT);
        if (!side.equals("BUY") && !side.equals("SELL")) {
            throw new OrderValidationException("side must be BUY or SELL");
        }

        if (request.type() == null) {
            throw new OrderValidationException("type is required");
        }
        String type = request.type().trim().toUpperCase(Locale.ROOT);
        if (!type.equals("LIMIT") && !type.equals("MARKET")) {
            throw new OrderValidationException("type must be LIMIT or MARKET");
        }

        if (request.quantity() == null || request.quantity() <= 0) {
            throw new OrderValidationException("quantity must be positive");
        }

        Double humanPrice = request.price();
        long priceTicks = 0;
        if (type.equals("LIMIT")) {
            if (humanPrice == null || humanPrice <= 0) {
                throw new OrderValidationException("LIMIT orders require a positive price");
            }
            priceTicks = PriceConverter.toTicks(humanPrice);
            if (priceTicks <= 0) {
                throw new OrderValidationException("LIMIT orders require a positive price");
            }
        }

        return new ValidatedOrder(
                request.order_id(),
                request.symbol().trim(),
                side,
                type,
                priceTicks,
                request.quantity());
    }

    public void validateCancelOrderId(String orderId) {
        if (orderId == null || orderId.isBlank()) {
            throw new OrderValidationException("order_id is required");
        }
        if (!orderId.matches("\\d+")) {
            throw new OrderValidationException("order_id must be numeric");
        }
        if (orderId.equals("0")) {
            throw new OrderValidationException("order_id must be non-zero");
        }
    }

    public record ValidatedOrder(
            String orderId,
            String symbol,
            String side,
            String type,
            long priceTicks,
            long quantity) {
    }
}
