package com.ledgerbull.position.risk;

import com.ledgerbull.position.config.RiskProperties;
import com.ledgerbull.position.entity.RiskEventEntity;
import com.ledgerbull.position.money.Money;
import com.ledgerbull.position.repository.RiskEventRepository;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Post-trade risk detection (Phase 5E). Reads settled net position / realized PnL after
 * recompute and persists durable {@link RiskEventEntity} rows on breach. Does not block
 * orders (kill switch is 5F). Never throws to the caller — ingestion/recompute must not break.
 *
 * <p>Idempotency / no-spam: record a new event only when the symbol <em>transitions into</em>
 * breach for that event type, or when an ongoing breach <em>materially worsens</em>
 * (|observed| grows by at least 10% of the limit). Recovering then re-breaching counts as a
 * new transition. Process-local state is seeded from the latest DB event on first evaluate.
 */
@Component
public class PostTradeRiskMonitor {

    private static final Logger log = LoggerFactory.getLogger(PostTradeRiskMonitor.class);

    public static final String TYPE_POSITION_BREACH = "POSITION_BREACH";
    public static final String TYPE_LOSS_BREACH = "LOSS_BREACH";
    public static final String SEVERITY_BREACH = "BREACH";

    private final RiskProperties limits;
    private final RiskEventRepository riskEventRepository;

    /** key = symbol|eventType → currently considered in-breach from last evaluation. */
    private final Map<String, Boolean> inBreachByKey = new ConcurrentHashMap<>();
    private final Map<String, Long> lastObservedByKey = new ConcurrentHashMap<>();

    public PostTradeRiskMonitor(RiskProperties limits, RiskEventRepository riskEventRepository) {
        this.limits = limits;
        this.riskEventRepository = riskEventRepository;
    }

    /**
     * Evaluate settled state for {@code symbol}. Safe to call from the recompute path;
     * failures are logged only.
     */
    public void evaluateAfterFill(String symbol, long netPosition, long realizedPnlTicks) {
        try {
            if (symbol == null || symbol.isBlank()) {
                return;
            }
            String sym = symbol.trim();
            evaluatePosition(sym, netPosition);
            evaluateLoss(sym, realizedPnlTicks);
        } catch (RuntimeException ex) {
            log.error("Post-trade risk evaluation failed for {}: {}", symbol, ex.getMessage(), ex);
        }
    }

    private void evaluatePosition(String symbol, long netPosition) {
        long limit = limits.maxNetPositionPerSymbol();
        boolean breached = Math.abs(netPosition) > limit;
        String key = key(symbol, TYPE_POSITION_BREACH);
        ensureSeeded(key, symbol, TYPE_POSITION_BREACH);

        boolean was = inBreachByKey.getOrDefault(key, false);
        if (breached && (!was || materiallyWorse(key, Math.abs(netPosition), limit))) {
            persist(
                    symbol,
                    TYPE_POSITION_BREACH,
                    netPosition,
                    limit,
                    "abs(net position " + netPosition + ") exceeds max " + limit);
        }
        inBreachByKey.put(key, breached);
        if (breached) {
            lastObservedByKey.put(key, Math.abs(netPosition));
        } else {
            lastObservedByKey.remove(key);
        }
    }

    private void evaluateLoss(String symbol, long realizedPnlTicks) {
        long limitUsd = limits.maxRealizedLossUsd();
        long limitTicks = Money.toTicks((double) limitUsd);
        // Loss breach: realized PnL more negative than -threshold (ticks).
        boolean breached = realizedPnlTicks < -limitTicks;
        String key = key(symbol, TYPE_LOSS_BREACH);
        ensureSeeded(key, symbol, TYPE_LOSS_BREACH);

        boolean was = inBreachByKey.getOrDefault(key, false);
        long lossMagnitude = breached ? -realizedPnlTicks : 0L;
        if (breached && (!was || materiallyWorse(key, lossMagnitude, limitTicks))) {
            persist(
                    symbol,
                    TYPE_LOSS_BREACH,
                    realizedPnlTicks,
                    limitTicks,
                    "realized pnl "
                            + Money.toHuman(realizedPnlTicks)
                            + " exceeds max loss "
                            + limitUsd
                            + " USD");
        }
        inBreachByKey.put(key, breached);
        if (breached) {
            lastObservedByKey.put(key, lossMagnitude);
        } else {
            lastObservedByKey.remove(key);
        }
    }

    /**
     * Seed in-breach from DB so a service restart does not re-insert the same open breach.
     */
    private void ensureSeeded(String key, String symbol, String eventType) {
        if (inBreachByKey.containsKey(key)) {
            return;
        }
        riskEventRepository
                .findTopBySymbolAndEventTypeOrderByCreatedAtDesc(symbol, eventType)
                .ifPresentOrElse(
                        latest -> {
                            inBreachByKey.put(key, true);
                            lastObservedByKey.put(key, Math.abs(latest.getObservedValue()));
                        },
                        () -> inBreachByKey.put(key, false));
    }

    private boolean materiallyWorse(String key, long currentMagnitude, long limit) {
        Long previous = lastObservedByKey.get(key);
        if (previous == null) {
            return true;
        }
        long threshold = Math.max(1L, limit / 10L); // 10% of limit
        return currentMagnitude >= previous + threshold;
    }

    private void persist(String symbol, String eventType, long observed, long limit, String detail) {
        RiskEventEntity event = new RiskEventEntity();
        event.setSymbol(symbol);
        event.setEventType(eventType);
        event.setSeverity(SEVERITY_BREACH);
        event.setObservedValue(observed);
        event.setLimitValue(limit);
        event.setDetail(detail);
        riskEventRepository.save(event);
        log.warn("Risk event recorded: {} {} observed={} limit={} — {}", eventType, symbol, observed, limit, detail);
    }

    private static String key(String symbol, String eventType) {
        return symbol + "|" + eventType;
    }
}
