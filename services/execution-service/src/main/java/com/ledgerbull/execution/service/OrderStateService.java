package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.EngineFill;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.OrderRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class OrderStateService {

    private static final Logger log = LoggerFactory.getLogger(OrderStateService.class);

    private final OrderRepository orderRepository;

    public OrderStateService(OrderRepository orderRepository) {
        this.orderRepository = orderRepository;
    }

    /**
     * Updates the submitting (taker) order from engine fills. Maker orders are not updated here (3G-3).
     */
    public void applyFillsToSubmittingOrder(OrderEntity order, List<EngineFill> fills) {
        long fillTotal = fills.stream().mapToLong(EngineFill::quantity).sum();
        long original = order.getOriginalQuantity();

        long filled;
        long remaining;
        String targetStatus;

        if (fillTotal == 0) {
            filled = 0L;
            remaining = original;
            targetStatus = OrderStatusTransitions.NEW;
        } else if (fillTotal >= original) {
            filled = original;
            remaining = 0L;
            targetStatus = OrderStatusTransitions.FILLED;
        } else {
            filled = fillTotal;
            remaining = original - fillTotal;
            targetStatus = OrderStatusTransitions.PARTIALLY_FILLED;
        }

        String currentStatus = order.getStatus();
        if (!OrderStatusTransitions.canTransition(currentStatus, targetStatus)) {
            log.warn(
                    "Ignoring invalid status transition {} -> {} for order {}",
                    currentStatus,
                    targetStatus,
                    order.getOrderId());
            return;
        }

        order.setFilledQuantity(filled);
        order.setRemainingQuantity(remaining);
        order.setStatus(targetStatus);
        orderRepository.save(order);
    }
}
