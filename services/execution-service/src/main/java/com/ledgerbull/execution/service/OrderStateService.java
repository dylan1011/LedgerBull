package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.EngineFill;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.OrderRepository;
import java.util.List;
import java.util.Optional;
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
     * Updates the submitting (taker) order from engine fills for this submit.
     */
    public void applyFillsToSubmittingOrder(OrderEntity order, List<EngineFill> fills) {
        long fillTotal = fills.stream().mapToLong(EngineFill::quantity).sum();
        applyComputedState(order, computeStateFromFilledQuantity(order.getOriginalQuantity(), fillTotal));
    }

    /**
     * Updates resting (maker) orders referenced in fills. Each fill accumulates on the maker's
     * existing filled quantity (makers can be filled across multiple submits).
     */
    public void applyFillsToMakerOrders(List<EngineFill> fills) {
        for (EngineFill fill : fills) {
            Optional<OrderEntity> maker = orderRepository.findByOrderId(fill.makerOrderId());
            if (maker.isEmpty()) {
                log.info("Maker order {} not found in DB, skipping state update", fill.makerOrderId());
                continue;
            }
            OrderEntity makerOrder = maker.get();
            long accumulatedFilled = makerOrder.getFilledQuantity() + fill.quantity();
            applyComputedState(makerOrder, computeStateFromFilledQuantity(makerOrder.getOriginalQuantity(), accumulatedFilled));
        }
    }

    private static ComputedOrderState computeStateFromFilledQuantity(long original, long filledTotal) {
        if (filledTotal <= 0) {
            return new ComputedOrderState(0L, original, OrderStatusTransitions.NEW);
        }
        if (filledTotal >= original) {
            return new ComputedOrderState(original, 0L, OrderStatusTransitions.FILLED);
        }
        return new ComputedOrderState(
                filledTotal, original - filledTotal, OrderStatusTransitions.PARTIALLY_FILLED);
    }

    private void applyComputedState(OrderEntity order, ComputedOrderState state) {
        String currentStatus = order.getStatus();
        if (!OrderStatusTransitions.canTransition(currentStatus, state.status())) {
            log.warn(
                    "Ignoring invalid status transition {} -> {} for order {}",
                    currentStatus,
                    state.status(),
                    order.getOrderId());
            return;
        }

        order.setFilledQuantity(state.filledQuantity());
        order.setRemainingQuantity(state.remainingQuantity());
        order.setStatus(state.status());
        orderRepository.save(order);
    }

    private record ComputedOrderState(long filledQuantity, long remainingQuantity, String status) {}
}
