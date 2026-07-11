package com.ledgerbull.execution.service;

import com.ledgerbull.execution.entity.FillEntity;
import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.web.dto.OrderDetailResponse;
import com.ledgerbull.execution.web.dto.OrderFillResponse;
import java.util.List;

final class OrderResponseMapper {

    private OrderResponseMapper() {
    }

    static OrderDetailResponse toDetail(OrderEntity order, List<FillEntity> fills) {
        return new OrderDetailResponse(
                order.getOrderId(),
                order.getSymbol(),
                order.getSide(),
                order.getOrderType(),
                toHumanPrice(order),
                order.getOriginalQuantity(),
                order.getFilledQuantity(),
                order.getRemainingQuantity(),
                order.getStatus(),
                order.getCreatedAt().toString(),
                order.getUpdatedAt().toString(),
                fills.stream().map(OrderResponseMapper::toFill).toList());
    }

    private static OrderFillResponse toFill(FillEntity fill) {
        return new OrderFillResponse(
                fill.getTakerOrderId(),
                fill.getMakerOrderId(),
                PriceConverter.fromTicks(fill.getFillPrice()),
                fill.getFillQuantity(),
                fill.getSymbol(),
                fill.getCreatedAt().toString());
    }

    private static Double toHumanPrice(OrderEntity order) {
        if (order.getPrice() == null) {
            return null;
        }
        return PriceConverter.fromTicks(order.getPrice());
    }
}
