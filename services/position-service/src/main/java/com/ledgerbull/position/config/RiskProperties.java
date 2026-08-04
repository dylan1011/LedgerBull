package com.ledgerbull.position.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Post-trade risk limits (Phase 5E). Same {@code ledgerbull.risk} names/semantics as
 * execution-service; only the limits needed for position + realized-loss monitoring.
 */
@ConfigurationProperties(prefix = "ledgerbull.risk")
public record RiskProperties(long maxNetPositionPerSymbol, long maxRealizedLossUsd) {
}
