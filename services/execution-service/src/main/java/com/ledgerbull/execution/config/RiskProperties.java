package com.ledgerbull.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Risk engine limits. Defaults live in {@code application.yml} under {@code ledgerbull.risk}.
 *
 * <p>{@code failClosed=true} means a check that cannot verify required data REJECTS (not allows).
 * {@code maxNotionalUsd} is human USD; compared after converting order price×qty consistently.
 */
@ConfigurationProperties(prefix = "ledgerbull.risk")
public record RiskProperties(
        long maxOrderQuantity,
        long maxNetPositionPerSymbol,
        long maxNotionalUsd,
        int fatFingerBandPercent,
        long maxRealizedLossUsd,
        boolean failClosed,
        String positionServiceBaseUrl) {
}
