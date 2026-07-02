package com.ledgerbull.marketdata.service;

import com.ledgerbull.marketdata.domain.MarketTick;
import com.ledgerbull.marketdata.repository.MarketTickRepository;
import com.ledgerbull.marketdata.web.dto.LatestPriceResponse;
import com.ledgerbull.marketdata.web.dto.TickResponse;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Persists ticks into TimescaleDB and maintains the latest-price cache in Redis.
 *
 * <p>Writes use a parameterized {@link JdbcTemplate} insert (never string concatenation) so that
 * every tick becomes a new row — JPA {@code save()} on the composite key would merge/overwrite
 * duplicates. Reads for history go through the JPA repository.
 */
@Service
public class MarketTickService {

    private static final Logger log = LoggerFactory.getLogger(MarketTickService.class);
    private static final String INSERT_SQL =
            "INSERT INTO market_ticks (time, symbol, price, volume, source) VALUES (?, ?, ?, ?, ?)";
    private static final String LATEST_KEY_PREFIX = "latest:price:";

    private final JdbcTemplate jdbcTemplate;
    private final StringRedisTemplate redisTemplate;
    private final MarketTickRepository repository;

    public MarketTickService(JdbcTemplate jdbcTemplate, StringRedisTemplate redisTemplate,
            MarketTickRepository repository) {
        this.jdbcTemplate = jdbcTemplate;
        this.redisTemplate = redisTemplate;
        this.repository = repository;
    }

    /** Persist a tick to TimescaleDB and refresh the Redis latest-price cache. */
    public void ingest(MarketTick tick) {
        jdbcTemplate.update(INSERT_SQL, tick.getTime(), tick.getSymbol(), tick.getPrice(),
                tick.getVolume(), tick.getSource());
        try {
            redisTemplate.opsForValue().set(LATEST_KEY_PREFIX + tick.getSymbol(),
                    Double.toString(tick.getPrice()));
        } catch (RuntimeException ex) {
            // Cache failures must not stop ingestion; the source of truth is TimescaleDB.
            log.warn("Failed to update Redis latest price for {}", tick.getSymbol(), ex);
        }
    }

    /** Latest cached price for a symbol, from Redis. */
    public Optional<LatestPriceResponse> latestPrice(String symbol) {
        String value = redisTemplate.opsForValue().get(LATEST_KEY_PREFIX + symbol);
        if (value == null) {
            return Optional.empty();
        }
        return Optional.of(new LatestPriceResponse(symbol, Double.parseDouble(value), "redis"));
    }

    /** Recent ticks for a symbol over the last {@code minutes}, newest first. */
    public List<TickResponse> history(String symbol, int minutes) {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
        return repository.findRecent(symbol, since).stream()
                .map(TickResponse::from)
                .toList();
    }
}
