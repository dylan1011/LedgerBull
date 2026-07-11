package com.ledgerbull.execution.web.dto;

import java.util.List;

public record OrderPageResponse(
        List<OrderDetailResponse> content,
        int page,
        int size,
        long total_elements,
        int total_pages) {
}
