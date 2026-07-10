package com.ledgerbull.execution.client;

public record EngineFill(
        String takerOrderId,
        String makerOrderId,
        String symbol,
        long priceTicks,
        long quantity) {
}
