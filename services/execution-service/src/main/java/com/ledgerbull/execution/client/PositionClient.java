package com.ledgerbull.execution.client;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ledgerbull.execution.config.RiskProperties;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * Reads current net position from position-service ({@code GET /api/positions/{symbol}}).
 *
 * <p>Reliability: config-driven connect/read timeouts, up to two retries with a short backoff,
 * ~1s in-memory cache per symbol. Never returns 0 on fetch failure — use
 * {@link NetPositionResult.Unavailable}. HTTP 404 (no row yet) is treated as net quantity 0.
 * Final failure after retries stays fail-closed at the risk layer.
 *
 * <p>A circuit breaker (Resilience4j) is planned for Phase 9 — not added here.
 */
@Component
public class PositionClient {

    private static final Logger log = LoggerFactory.getLogger(PositionClient.class);

    private static final long CACHE_TTL_MS = 1_000L;
    /** Tiny pause before a retry so a momentary hiccup can clear. */
    private static final long RETRY_BACKOFF_MS = 75L;
    /** Initial attempt + up to this many retries. */
    private static final int MAX_RETRIES = 2;

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PositionClient(RiskProperties riskProperties) {
        Duration connectTimeout = Duration.ofMillis(Math.max(1L, riskProperties.positionServiceConnectTimeoutMs()));
        Duration readTimeout = Duration.ofMillis(Math.max(1L, riskProperties.positionServiceReadTimeoutMs()));
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(connectTimeout);
        requestFactory.setReadTimeout(readTimeout);
        this.restClient = RestClient.builder()
                .baseUrl(riskProperties.positionServiceBaseUrl())
                .requestFactory(requestFactory)
                .build();
        log.info(
                "PositionClient timeouts: connect={}ms read={}ms (retries={}, backoff={}ms, cache={}ms)",
                connectTimeout.toMillis(),
                readTimeout.toMillis(),
                MAX_RETRIES,
                RETRY_BACKOFF_MS,
                CACHE_TTL_MS);
    }

    /**
     * Current net quantity for {@code symbol}, or unavailable if position-service cannot be
     * reached after retries.
     */
    public NetPositionResult getNetPosition(String symbol) {
        if (symbol == null || symbol.isBlank()) {
            return NetPositionResult.unavailable("blank symbol");
        }
        String key = symbol.trim();

        CacheEntry cached = cache.get(key);
        long now = System.currentTimeMillis();
        if (cached != null && now - cached.fetchedAtMs() < CACHE_TTL_MS) {
            return NetPositionResult.available(cached.netQuantity());
        }

        NetPositionResult last = fetchOnce(key);
        if (last instanceof NetPositionResult.Available available) {
            cache.put(key, new CacheEntry(available.netQuantity(), now));
            return last;
        }

        for (int attempt = 1; attempt <= MAX_RETRIES; attempt++) {
            log.warn(
                    "Position fetch failed for {} ({}); retry {}/{} after {}ms",
                    key,
                    ((NetPositionResult.Unavailable) last).detail(),
                    attempt,
                    MAX_RETRIES,
                    RETRY_BACKOFF_MS);
            sleepBackoff();
            last = fetchOnce(key);
            if (last instanceof NetPositionResult.Available available) {
                cache.put(key, new CacheEntry(available.netQuantity(), System.currentTimeMillis()));
                return last;
            }
        }
        // Fail-closed signal for RiskEngine — do not invent a zero position.
        return last;
    }

    private void sleepBackoff() {
        try {
            Thread.sleep(RETRY_BACKOFF_MS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    private NetPositionResult fetchOnce(String symbol) {
        try {
            PositionBody body = restClient.get()
                    .uri("/api/positions/{symbol}", symbol)
                    .retrieve()
                    .body(PositionBody.class);

            if (body == null) {
                return NetPositionResult.unavailable("empty response body");
            }
            return NetPositionResult.available(body.net_quantity());
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                // No position row yet → flat (known zero), not a fetch failure.
                return NetPositionResult.available(0L);
            }
            return NetPositionResult.unavailable("HTTP " + ex.getStatusCode().value());
        } catch (RestClientException ex) {
            return NetPositionResult.unavailable(ex.getClass().getSimpleName() + ": " + ex.getMessage());
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    private record PositionBody(long net_quantity) {
    }

    private record CacheEntry(long netQuantity, long fetchedAtMs) {
    }
}
