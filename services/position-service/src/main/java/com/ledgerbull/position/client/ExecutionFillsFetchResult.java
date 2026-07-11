package com.ledgerbull.position.client;

import com.ledgerbull.position.client.dto.ExecutionOrderFill;
import java.util.List;

public record ExecutionFillsFetchResult(List<ExecutionOrderFill> fills, boolean executionReachable) {

    public static ExecutionFillsFetchResult unreachable() {
        return new ExecutionFillsFetchResult(List.of(), false);
    }

    public static ExecutionFillsFetchResult success(List<ExecutionOrderFill> fills) {
        return new ExecutionFillsFetchResult(fills, true);
    }
}
