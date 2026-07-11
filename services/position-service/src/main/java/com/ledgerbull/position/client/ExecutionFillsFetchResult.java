package com.ledgerbull.position.client;

import com.ledgerbull.position.client.dto.ExecutionIngestFill;
import java.util.List;

public record ExecutionFillsFetchResult(List<ExecutionIngestFill> fills, boolean executionReachable) {

    public static ExecutionFillsFetchResult unreachable() {
        return new ExecutionFillsFetchResult(List.of(), false);
    }

    public static ExecutionFillsFetchResult success(List<ExecutionIngestFill> fills) {
        return new ExecutionFillsFetchResult(fills, true);
    }
}
