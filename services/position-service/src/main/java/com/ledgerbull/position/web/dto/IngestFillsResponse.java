package com.ledgerbull.position.web.dto;

public record IngestFillsResponse(int seen, int ingested, int duplicates, boolean executionReachable) {
}
