package com.ledgerbull.marketdata.service;

import com.ledgerbull.marketdata.domain.MarketTick;
import com.ledgerbull.marketdata.repository.MarketTickRepository;
import com.ledgerbull.marketdata.web.dto.LatestPriceResponse;
import com.ledgerbull.marketdata.web.dto.TickResponse;
import jakarta.annotation.PreDestroy;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

/**
 * Maintains the Redis latest-price cache and hands ticks off to {@link TickBatchWriter} for
 * batched persistence into TimescaleDB. Reads for history go through the JPA repository.
 *
 * <p>Redis is a cache, not the source of truth, so it must never impede ingestion. Each tick is
 * buffered for DB persistence first, then the latest-price cache is updated <b>off the ingestion
 * thread</b> via a bounded single-thread executor — so a slow or down Redis (which can block until
 * the client times out) can never stall the market-data feed. Cache updates that can't keep up are
 * simply dropped (the newest price will refresh the cache once Redis recovers).
 */
@Service
public class MarketTickService {

    private static final Logger log = LoggerFactory.getLogger(MarketTickService.class);
    private static final String LATEST_KEY_PREFIX = "latest:price:";

    private final StringRedisTemplate redisTemplate;
    private final MarketTickRepository repository;
    private final TickBatchWriter batchWriter;

    // Off-thread, bounded cache updater so Redis latency/outages never block the ingestion path.
    private final ThreadPoolExecutor cacheExecutor = new ThreadPoolExecutor(
            1, 1, 0L, TimeUnit.MILLISECONDS, new ArrayBlockingQueue<>(1000),
            r -> {
                Thread t = new Thread(r, "marketdata-redis-cache");
                t.setDaemon(true);
                return t;
            },
            new ThreadPoolExecutor.DiscardOldestPolicy());
    private final AtomicLong cacheFailures = new AtomicLong();

    public MarketTickService(StringRedisTemplate redisTemplate, MarketTickRepository repository,
            TickBatchWriter batchWriter) {
        this.redisTemplate = redisTemplate;
        this.repository = repository;
        this.batchWriter = batchWriter;
    }

    /** Buffer the tick for batched persistence, then refresh the Redis latest-price cache off-thread. */
    public void ingest(MarketTick tick) {
        batchWriter.submit(tick);
        cacheExecutor.execute(() -> updateLatestPrice(tick));
    }

    private void updateLatestPrice(MarketTick tick) {
        try {
            redisTemplate.opsForValue().set(LATEST_KEY_PREFIX + tick.getSymbol(),
                    Double.toString(tick.getPrice()));
        } catch (RuntimeException ex) {
            // Cache failures must not stop ingestion; the source of truth is TimescaleDB.
            long failures = cacheFailures.incrementAndGet();
            if (failures % 1000 == 1) {
                log.warn("Failed to update Redis latest price for {} (total cache failures: {}): {}",
                        tick.getSymbol(), failures, ex.getMessage());
            }
        }
    }

    /** Latest cached price for a symbol, from Redis. Returns empty if Redis is unavailable. */
    public Optional<LatestPriceResponse> latestPrice(String symbol) {
        try {
            String value = redisTemplate.opsForValue().get(LATEST_KEY_PREFIX + symbol);
            if (value == null) {
                return Optional.empty();
            }
            return Optional.of(new LatestPriceResponse(symbol, Double.parseDouble(value), "redis"));
        } catch (RuntimeException ex) {
            log.warn("Failed to read latest price for {} from Redis: {}", symbol, ex.getMessage());
            return Optional.empty();
        }
    }

    @PreDestroy
    public void shutdown() {
        cacheExecutor.shutdownNow();
    }

    /** Recent ticks for a symbol over the last {@code minutes}, newest first. */
    public List<TickResponse> history(String symbol, int minutes) {
        OffsetDateTime since = OffsetDateTime.now(ZoneOffset.UTC).minusMinutes(minutes);
        return repository.findRecent(symbol, since).stream()
                .map(TickResponse::from)
                .toList();
    }
}
