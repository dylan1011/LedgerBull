package com.ledgerbull.execution.web.dto;

public record OrderRequest(
        String order_id,
        String symbol,
        String side,
        String type,
        Double price,
        Long quantity) {
}
