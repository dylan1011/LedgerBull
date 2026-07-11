package com.ledgerbull.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.ledgerbull.execution.client.MatchingEngineClient;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.FillRepository;
import com.ledgerbull.execution.repository.OrderRepository;
import com.ledgerbull.execution.service.ExecutionService;
import com.ledgerbull.execution.service.OrderStateService;
import com.ledgerbull.execution.service.OrderValidationService;
import com.ledgerbull.execution.web.dto.CancelOrderResponse;
import com.ledgerbull.execution.web.error.OrderCancelConflictException;
import com.ledgerbull.execution.web.error.OrderNotFoundException;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ExecutionCancelQueryTest {

    @Mock
    private MatchingEngineClient engineClient;

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private FillRepository fillRepository;

    @Mock
    private OrderStateService orderStateService;

    private ExecutionService executionService;

    @BeforeEach
    void setUp() {
        executionService = new ExecutionService(
                new OrderValidationService(),
                engineClient,
                orderRepository,
                fillRepository,
                orderStateService);
    }

    @Test
    void getOrderNotFound() {
        when(orderRepository.findByOrderId("999")).thenReturn(Optional.empty());

        assertThrows(OrderNotFoundException.class, () -> executionService.getOrder("999"));
    }

    @Test
    void cannotCancelFilledOrder() {
        OrderEntity order = filledOrder("42");
        when(orderRepository.findByOrderId("42")).thenReturn(Optional.of(order));

        assertThrows(OrderCancelConflictException.class, () -> executionService.cancelOrder("42"));
        verify(engineClient, never()).cancelOrder(any());
        verify(orderRepository, never()).save(any());
    }

    @Test
    void cancelRestingOrderUpdatesStatus() {
        OrderEntity order = newOrder("43");
        when(orderRepository.findByOrderId("43")).thenReturn(Optional.of(order));
        when(engineClient.cancelOrder("43")).thenReturn(new CancelOrderResponse(true));
        when(fillRepository.findByOrderRefId(1L)).thenReturn(List.of());
        when(orderRepository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var response = executionService.cancelOrder("43");

        assertEquals("CANCELLED", response.status());
        assertEquals("43", response.order_id());
    }

    private static OrderEntity newOrder(String orderId) {
        OrderEntity order = new OrderEntity();
        order.setId(1L);
        order.setOrderId(orderId);
        order.setSymbol("BTC-USD");
        order.setSide("SELL");
        order.setOrderType("LIMIT");
        order.setPrice(800100L);
        order.setOriginalQuantity(5L);
        order.setFilledQuantity(0L);
        order.setRemainingQuantity(5L);
        order.setStatus("NEW");
        order.setCreatedAt(java.time.OffsetDateTime.parse("2026-07-11T12:00:00Z"));
        order.setUpdatedAt(java.time.OffsetDateTime.parse("2026-07-11T12:00:00Z"));
        return order;
    }

    private static OrderEntity filledOrder(String orderId) {
        OrderEntity order = newOrder(orderId);
        order.setStatus("FILLED");
        order.setFilledQuantity(5L);
        order.setRemainingQuantity(0L);
        return order;
    }
}
