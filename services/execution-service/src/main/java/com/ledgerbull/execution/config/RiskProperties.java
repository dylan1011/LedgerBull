package com.ledgerbull.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk engine limits (Phase 5A). Defaults live in {@code application.yml} under
 * {@code ledgerbull.risk}; override via env/config. Checks that use these are added in 5B–5G.
 *
 * <p>{@code failClosed=true} means a check that cannot verify required data should later REJECT
 * (not allow). Enforcement of that policy arrives with each individual check.
 */
@ConfigurationProperties(prefix = "ledgerbull.risk")
public record RiskProperties(
        long maxOrderQuantity,
        long maxNetPositionPerSymbol,
        long maxNotionalUsd,
        int fatFingerBandPercent,
        long maxRealizedLossUsd,
        boolean failClosed) {
}
