package com.ledgerbull.position.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionOrderFill(
        String taker_order_id,
        String maker_order_id,
        double price,
        long quantity,
        String symbol,
        String created_at) {
}
