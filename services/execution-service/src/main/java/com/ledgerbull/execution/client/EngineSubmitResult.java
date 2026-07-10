package com.ledgerbull.execution.client;

import com.ledgerbull.execution.web.dto.SubmitOrderResponse;
import java.util.List;

public record EngineSubmitResult(SubmitOrderResponse response, List<EngineFill> fills) {
}
