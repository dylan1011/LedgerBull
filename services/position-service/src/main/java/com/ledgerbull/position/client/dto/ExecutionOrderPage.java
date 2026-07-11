package com.ledgerbull.position.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record ExecutionOrderPage(
        List<ExecutionOrderDetail> content,
        int page,
        int size,
        long total_elements,
        int total_pages) {
}
