package com.ledgerbull.execution.service;

import com.ledgerbull.execution.client.EngineFill;
import com.ledgerbull.execution.entity.FillEntity;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.FillRepository;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists match results atomically. Lives in its own Spring bean so {@link Transactional}
 * applies via the proxy when called from {@link ExecutionService}.
 */
@Service
public class OrderMatchPersistenceService {

    private final FillRepository fillRepository;
    private final OrderStateService orderStateService;

    public OrderMatchPersistenceService(FillRepository fillRepository, OrderStateService orderStateService) {
        this.fillRepository = fillRepository;
        this.orderStateService = orderStateService;
    }

    @Transactional
    public void persistMatchResults(OrderEntity takerOrder, List<EngineFill> fills) {
        saveFills(takerOrder.getId(), fills);
        orderStateService.applyFillsToSubmittingOrder(takerOrder, fills);
        orderStateService.applyFillsToMakerOrders(fills);
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
}
