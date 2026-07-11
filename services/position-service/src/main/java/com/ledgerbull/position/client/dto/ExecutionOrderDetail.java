package com.ledgerbull.position.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionOrderDetail(
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
        List<ExecutionOrderFill> fills) {
}
