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
 * <p>Reliability (Phase 5B): short connect/read timeout, one retry on failure, ~1s in-memory
 * cache per symbol. Never returns 0 on fetch failure — use {@link NetPositionResult.Unavailable}.
 * HTTP 404 (no row yet) is treated as net quantity 0.
 *
 * <p>A circuit breaker (Resilience4j) is planned for Phase 9 — not added here.
 */
@Component
public class PositionClient {

    private static final Logger log = LoggerFactory.getLogger(PositionClient.class);

    private static final Duration CONNECT_TIMEOUT = Duration.ofMillis(400);
    private static final Duration READ_TIMEOUT = Duration.ofMillis(400);
    private static final long CACHE_TTL_MS = 1_000L;

    private final RestClient restClient;
    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public PositionClient(RiskProperties riskProperties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout(CONNECT_TIMEOUT);
        requestFactory.setReadTimeout(READ_TIMEOUT);
        this.restClient = RestClient.builder()
                .baseUrl(riskProperties.positionServiceBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }

    /**
     * Current net quantity for {@code symbol}, or unavailable if position-service cannot be
     * reached after one retry.
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

        NetPositionResult first = fetchOnce(key);
        if (first instanceof NetPositionResult.Available available) {
            cache.put(key, new CacheEntry(available.netQuantity(), now));
            return first;
        }

        log.warn("Position fetch failed for {} ({}); retrying once", key, ((NetPositionResult.Unavailable) first).detail());
        NetPositionResult second = fetchOnce(key);
        if (second instanceof NetPositionResult.Available available) {
            cache.put(key, new CacheEntry(available.netQuantity(), System.currentTimeMillis()));
            return second;
        }
        return second;
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
