package com.ledgerbull.execution.risk;

import com.ledgerbull.execution.client.MarketPriceClient;
import com.ledgerbull.execution.client.NetPositionResult;
import com.ledgerbull.execution.client.PositionClient;
import com.ledgerbull.execution.config.RiskProperties;
import com.ledgerbull.execution.service.PriceConverter;
import jakarta.annotation.PostConstruct;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * In-process pre-trade risk engine. Evaluates size, notional, position, and fat-finger before
 * an order reaches the matching engine. Fail-closed when required data is unavailable (default).
 */
@Component
public class RiskEngine {

    private static final Logger log = LoggerFactory.getLogger(RiskEngine.class);

    private final RiskProperties limits;
    private final PositionClient positionClient;
    private final MarketPriceClient marketPriceClient;

    public RiskEngine(
            RiskProperties limits, PositionClient positionClient, MarketPriceClient marketPriceClient) {
        this.limits = limits;
        this.positionClient = positionClient;
        this.marketPriceClient = marketPriceClient;
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
     * Evaluate a new order against pre-trade limits. Returns the first reject reason, or allow.
     *
     * <p>{@code price} is human quote units for LIMIT orders; null/ignored for MARKET.
     * Position projection uses the full requested quantity as the worst-case fill (a limit may
     * not fully fill, but pre-trade caps at the requested size). Fat-finger applies to LIMIT
     * only; MARKET skips the price-distance test but still runs size/notional/position.
     */
    public RiskDecision evaluateNewOrder(
            String symbol, String side, String type, Double price, Long quantity) {
        if (quantity == null || quantity <= 0) {
            return RiskDecision.reject("order quantity must be positive");
        }

        // 1. Order-size (local)
        if (quantity > limits.maxOrderQuantity()) {
            return RiskDecision.reject(
                    "order quantity " + quantity + " exceeds max " + limits.maxOrderQuantity());
        }

        boolean isLimit = type != null && type.equalsIgnoreCase("LIMIT");
        boolean isBuy = side != null && side.equalsIgnoreCase("BUY");

        // Resolve a human price for notional (LIMIT = order price; MARKET = Redis mid).
        Optional<Double> priceForNotional = Optional.empty();
        if (isLimit) {
            if (price == null || !(price > 0.0) || Double.isNaN(price) || Double.isInfinite(price)) {
                return RiskDecision.reject("LIMIT orders require a positive price for notional check");
            }
            priceForNotional = Optional.of(price);
        } else {
            Optional<Double> market = marketPriceClient.getLatestHumanPrice(symbol);
            if (market.isEmpty()) {
                if (limits.failClosed()) {
                    return RiskDecision.reject("market price unavailable (fail-closed)");
                }
            } else {
                priceForNotional = market;
            }
        }

        // 2. Notional — human USD: price * quantity vs maxNotionalUsd
        if (priceForNotional.isPresent()) {
            double humanPrice = priceForNotional.get();
            double notional = humanPrice * quantity;
            if (notional > limits.maxNotionalUsd()) {
                return RiskDecision.reject(
                        "order notional " + formatMoney(notional) + " exceeds max " + limits.maxNotionalUsd());
            }
        }

        // 3. Position limit — project worst-case full fill of requested quantity
        NetPositionResult position = positionClient.getNetPosition(symbol);
        if (position instanceof NetPositionResult.Unavailable) {
            if (limits.failClosed()) {
                return RiskDecision.reject("position data unavailable (fail-closed)");
            }
        } else if (position instanceof NetPositionResult.Available available) {
            long delta = isBuy ? quantity : -quantity;
            long projected = available.netQuantity() + delta;
            if (Math.abs(projected) > limits.maxNetPositionPerSymbol()) {
                return RiskDecision.reject(
                        "projected position " + projected + " exceeds max " + limits.maxNetPositionPerSymbol());
            }
        }

        // 4. Fat-finger — LIMIT only (MARKET has no order price to compare)
        if (isLimit) {
            Optional<Double> market = marketPriceClient.getLatestHumanPrice(symbol);
            if (market.isEmpty()) {
                if (limits.failClosed()) {
                    return RiskDecision.reject("market price unavailable (fail-closed)");
                }
            } else {
                double marketPrice = market.get();
                double orderPrice = price;
                double pctAway = Math.abs(orderPrice - marketPrice) / marketPrice * 100.0;
                if (pctAway > limits.fatFingerBandPercent()) {
                    return RiskDecision.reject(
                            "price "
                                    + formatMoney(orderPrice)
                                    + " is >"
                                    + limits.fatFingerBandPercent()
                                    + "% from market "
                                    + formatMoney(marketPrice));
                }
            }
        }

        return RiskDecision.allow();
    }

    public RiskProperties limits() {
        return limits;
    }

    private static String formatMoney(double value) {
        // Avoid scientific notation in reject reasons; tick precision is enough.
        long ticks = PriceConverter.toTicks(value);
        return String.format("%.2f", PriceConverter.fromTicks(ticks));
    }
}
