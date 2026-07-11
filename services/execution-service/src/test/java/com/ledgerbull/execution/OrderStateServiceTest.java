package com.ledgerbull.execution;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import com.ledgerbull.execution.client.EngineFill;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.OrderRepository;
import com.ledgerbull.execution.service.OrderStateService;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class OrderStateServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderStateService orderStateService;

    @BeforeEach
    void setUp() {
        orderStateService = new OrderStateService(orderRepository);
        lenient().when(orderRepository.save(any(OrderEntity.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));
    }

    @Test
    void noFillsKeepsNew() {
        OrderEntity order = newOrder("1", 5L);

        orderStateService.applyFillsToSubmittingOrder(order, List.of());

        assertEquals("NEW", order.getStatus());
        assertEquals(0L, order.getFilledQuantity());
        assertEquals(5L, order.getRemainingQuantity());
    }

    @Test
    void fullFillSetsFilled() {
        OrderEntity order = newOrder("2", 5L);
        List<EngineFill> fills = List.of(fill("2", "1", 5L));

        orderStateService.applyFillsToSubmittingOrder(order, fills);

        assertEquals("FILLED", order.getStatus());
        assertEquals(5L, order.getFilledQuantity());
        assertEquals(0L, order.getRemainingQuantity());
    }

    @Test
    void partialFillSetsPartiallyFilled() {
        OrderEntity order = newOrder("3", 10L);
        List<EngineFill> fills = List.of(fill("3", "1", 3L));

        orderStateService.applyFillsToSubmittingOrder(order, fills);

        assertEquals("PARTIALLY_FILLED", order.getStatus());
        assertEquals(3L, order.getFilledQuantity());
        assertEquals(7L, order.getRemainingQuantity());
    }

    @Test
    void filledIsTerminal() {
        OrderEntity order = newOrder("4", 5L);
        order.setStatus("FILLED");
        order.setFilledQuantity(5L);
        order.setRemainingQuantity(0L);

        orderStateService.applyFillsToSubmittingOrder(order, List.of(fill("4", "1", 1L)));

        assertEquals("FILLED", order.getStatus());
        assertEquals(5L, order.getFilledQuantity());
        assertEquals(0L, order.getRemainingQuantity());
        verify(orderRepository, never()).save(any());
    }

    private static OrderEntity newOrder(String orderId, long quantity) {
        OrderEntity order = new OrderEntity();
        order.setOrderId(orderId);
        order.setOriginalQuantity(quantity);
        order.setFilledQuantity(0L);
        order.setRemainingQuantity(quantity);
        order.setStatus("NEW");
        return order;
    }

    private static EngineFill fill(String taker, String maker, long quantity) {
        return new EngineFill(taker, maker, "BTC-USD", 15000L, quantity);
    }
}
