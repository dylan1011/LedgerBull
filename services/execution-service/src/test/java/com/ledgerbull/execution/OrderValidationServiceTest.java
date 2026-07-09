package com.ledgerbull.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ledgerbull.execution.service.OrderValidationService;
import com.ledgerbull.execution.service.OrderValidationService.ValidatedOrder;
import com.ledgerbull.execution.web.dto.OrderRequest;
import com.ledgerbull.execution.web.error.OrderValidationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class OrderValidationServiceTest {

    private OrderValidationService validationService;

    @BeforeEach
    void setUp() {
        validationService = new OrderValidationService();
    }

    @Test
    void validLimitOrderPasses() {
        ValidatedOrder validated = validationService.validate(
                new OrderRequest("1", "BTC-USD", "SELL", "LIMIT", 105.0, 5L));
        assertEquals("1", validated.orderId());
        assertEquals(10500L, validated.priceTicks());
        assertEquals(5L, validated.quantity());
    }

    @Test
    void rejectsMissingOrderId() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest(null, "BTC-USD", "SELL", "LIMIT", 105.0, 5L)));
        assertEquals("order_id is required", ex.getMessage());
    }

    @Test
    void rejectsNonNumericOrderId() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("abc", "BTC-USD", "SELL", "LIMIT", 105.0, 5L)));
        assertEquals("order_id must be numeric", ex.getMessage());
    }

    @Test
    void rejectsBlankSymbol() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "  ", "SELL", "LIMIT", 105.0, 5L)));
        assertEquals("symbol is required", ex.getMessage());
    }

    @Test
    void rejectsInvalidSide() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "BTC-USD", "HOLD", "LIMIT", 105.0, 5L)));
        assertEquals("side must be BUY or SELL", ex.getMessage());
    }

    @Test
    void rejectsInvalidType() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "BTC-USD", "SELL", "STOP", 105.0, 5L)));
        assertEquals("type must be LIMIT or MARKET", ex.getMessage());
    }

    @Test
    void rejectsNonPositiveQuantity() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "BTC-USD", "SELL", "LIMIT", 105.0, -5L)));
        assertEquals("quantity must be positive", ex.getMessage());
    }

    @Test
    void rejectsLimitWithoutPrice() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "BTC-USD", "SELL", "LIMIT", null, 5L)));
        assertEquals("LIMIT orders require a positive price", ex.getMessage());
    }

    @Test
    void rejectsLimitWithZeroPrice() {
        OrderValidationException ex = assertThrows(OrderValidationException.class,
                () -> validationService.validate(
                        new OrderRequest("1", "BTC-USD", "SELL", "LIMIT", 0.0, 5L)));
        assertEquals("LIMIT orders require a positive price", ex.getMessage());
    }

    @Test
    void validMarketOrderPassesWithoutPrice() {
        ValidatedOrder validated = validationService.validate(
                new OrderRequest("2", "BTC-USD", "BUY", "MARKET", null, 3L));
        assertEquals(0L, validated.priceTicks());
    }
}
