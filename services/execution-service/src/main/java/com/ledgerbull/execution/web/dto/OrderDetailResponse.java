package com.ledgerbull.execution.web.dto;

import java.util.List;

public record OrderDetailResponse(
        String order_id,
        String symbol,
        String side,
        String type,
        Double price,
        long original_quantity,
        long filled_quantity,
        long remaining_quantity,
        String status,
        String created_at,
        String updated_at,
        List<OrderFillResponse> fills) {

    public OrderDetailResponse withoutFills() {
        return new OrderDetailResponse(
                order_id,
                symbol,
                side,
                type,
                price,
                original_quantity,
                filled_quantity,
                remaining_quantity,
                status,
                created_at,
                updated_at,
                List.of());
    }
}
