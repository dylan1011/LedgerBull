package com.ledgerbull.position.web.dto;

public record LotResponse(
        String symbol,
        String side,
        long original_quantity,
        long remaining_quantity,
        long price,
        String price_human,
        long sequence_no,
        String source_fill_id) {
}
