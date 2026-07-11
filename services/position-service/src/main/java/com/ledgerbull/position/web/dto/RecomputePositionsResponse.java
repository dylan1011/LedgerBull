package com.ledgerbull.position.web.dto;

import java.util.List;

public record RecomputePositionsResponse(int symbols_updated, List<SymbolNetQuantityResponse> positions) {
}
