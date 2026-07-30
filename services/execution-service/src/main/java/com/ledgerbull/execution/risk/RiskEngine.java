package com.ledgerbull.execution.risk;

import com.ledgerbull.execution.config.RiskProperties;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-process risk engine (Phase 5). Lives in execution-service; checks are added in 5B–5G.
 * Not wired into the order path yet (5A scaffolding only).
 */
@Component
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);

    private final RiskProperties limits;

    public RiskEngine(RiskProperties limits) {
        this.limits = limits;
    }

    @PostConstruct
    void logLimits() {
        log.info(
                "RiskEngine initialized with limits: maxOrderQty={}, maxNetPos/symbol={}, "
                        + "maxNotionalUsd={}, fatFingerBand={}%, maxRealizedLossUsd={}, failClosed={}",
                limits.maxOrderQuantity(),
                limits.maxNetPositionPerSymbol(),
                limits.maxNotionalUsd(),
                limits.fatFingerBandPercent(),
                limits.maxRealizedLossUsd(),
                limits.failClosed());
    }

    /**
     * Evaluate a new order against risk limits. Phase 5A: always allows — individual checks
     * (size, position, notional, fat-finger, loss) land in 5B–5G.
     */
    public RiskDecision evaluateNewOrder(
            String symbol, String side, String type, Double price, Long quantity) {
        // 5A: no rules yet — order path still does not call this method.
        return RiskDecision.allow();
    }

    public RiskProperties limits() {
        return limits;
    }
}
