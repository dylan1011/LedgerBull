package com.ledgerbull.position.client;

import com.ledgerbull.position.money.Money;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the latest market price from Redis ({@code latest:price:{symbol}}).
 *
 * <p>Market-data stores human decimals as strings (e.g. {@code "64649.34"}); this client converts
 * them to ticks via {@link Money#toTicks(double)}. Missing/unreachable/unparseable prices return
 * empty — never {@code 0}.
 */
@Component
public class MarketPriceClient {

    private static final Logger log = LoggerFactory.getLogger(MarketPriceClient.class);
    private static final String LATEST_KEY_PREFIX = "latest:price:";

    private final StringRedisTemplate redisTemplate;

    public MarketPriceClient(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public Optional<Long> getLatestPriceTicks(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return Optional.empty();
        }
        String key = LATEST_KEY_PREFIX + symbol.trim();
        try {
            String raw = redisTemplate.opsForValue().get(key);
            if (raw == null || raw.isBlank()) {
                log.warn("No latest price in Redis for key {}", key);
                return Optional.empty();
            }
            double humanPrice = Double.parseDouble(raw.trim());
            if (!(humanPrice > 0.0) || Double.isNaN(humanPrice) || Double.isInfinite(humanPrice)) {
                log.warn("Invalid latest price '{}' for key {}", raw, key);
                return Optional.empty();
            }
            return Optional.of(Money.toTicks(humanPrice));
        } catch (NumberFormatException ex) {
            log.warn("Unparseable latest price for key {}: {}", key, ex.getMessage());
            return Optional.empty();
        } catch (RuntimeException ex) {
            log.warn("Redis unreachable while reading {}: {}", key, ex.getMessage());
            return Optional.empty();
        }
    }
}
