package com.ledgerbull.execution.web.dto;

import java.util.List;

public record SubmitOrderResponse(
        List<FillResponse> fills,
        long resting_quantity,
        boolean accepted,
        String reject_reason) {
}
