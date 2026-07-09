package com.ledgerbull.execution.web.dto;

import java.util.List;

public record BookResponse(List<BookLevelResponse> bids, List<BookLevelResponse> asks) {
}
