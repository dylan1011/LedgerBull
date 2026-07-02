package com.ledgerbull.marketdata.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.ledgerbull.marketdata.config.MarketDataProperties;
import com.ledgerbull.marketdata.service.MarketTickService;
import jakarta.annotation.PreDestroy;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Connects to the Coinbase Exchange market-data websocket, subscribes to the configured symbols'
 * {@code ticker} channel, and feeds normalized ticks into {@link MarketTickService}.
 *
 * <p>Runs off the main thread (async websocket + a scheduled executor) so it never stalls startup,
 * and self-heals via reconnect-with-exponential-backoff on close/error.
 */
@Component
public class CoinbaseWebSocketClient {

    private static final Logger log = LoggerFactory.getLogger(CoinbaseWebSocketClient.class);
    private static final Duration MIN_BACKOFF = Duration.ofSeconds(1);
    private static final Duration MAX_BACKOFF = Duration.ofSeconds(30);

    private final MarketDataProperties properties;
    private final TickNormalizer normalizer;
    private final MarketTickService tickService;
    private final ObjectMapper objectMapper;

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final ScheduledExecutorService scheduler =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "marketdata-ws-reconnect");
                t.setDaemon(true);
                return t;
            });
    private final AtomicInteger attempt = new AtomicInteger(0);
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    private volatile WebSocket webSocket;

    public CoinbaseWebSocketClient(MarketDataProperties properties, TickNormalizer normalizer,
            MarketTickService tickService, ObjectMapper objectMapper) {
        this.properties = properties;
        this.normalizer = normalizer;
        this.tickService = tickService;
        this.objectMapper = objectMapper;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        log.info("Starting market-data websocket client -> {} symbols={}",
                properties.getWebsocketUrl(), properties.getSymbols());
        connect();
    }

    private void connect() {
        if (shuttingDown.get()) {
            return;
        }
        httpClient.newWebSocketBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .buildAsync(URI.create(properties.getWebsocketUrl()), new FeedListener())
                .whenComplete((ws, err) -> {
                    if (err != null) {
                        log.warn("Websocket connect failed: {}", err.toString());
                        scheduleReconnect();
                    } else {
                        this.webSocket = ws;
                    }
                });
    }

    private void scheduleReconnect() {
        if (shuttingDown.get()) {
            return;
        }
        int n = attempt.incrementAndGet();
        long delaySeconds = Math.min(MAX_BACKOFF.getSeconds(),
                MIN_BACKOFF.getSeconds() * (1L << Math.min(n - 1, 5)));
        log.info("Reconnecting to market-data feed in {}s (attempt {})", delaySeconds, n);
        scheduler.schedule(this::connect, delaySeconds, TimeUnit.SECONDS);
    }

    private String buildSubscribeMessage() {
        ObjectNode root = objectMapper.createObjectNode();
        root.put("type", "subscribe");
        ArrayNode productIds = root.putArray("product_ids");
        properties.getSymbols().forEach(productIds::add);
        ArrayNode channels = root.putArray("channels");
        channels.add("ticker");
        return root.toString();
    }

    private void handleMessage(String message) {
        try {
            JsonNode node = objectMapper.readTree(message);
            normalizer.normalize(node).ifPresent(tickService::ingest);
        } catch (Exception ex) {
            // A single malformed message must never break the read loop.
            log.warn("Error handling market-data message", ex);
        }
    }

    @PreDestroy
    public void shutdown() {
        shuttingDown.set(true);
        scheduler.shutdownNow();
        WebSocket ws = this.webSocket;
        if (ws != null) {
            ws.sendClose(WebSocket.NORMAL_CLOSURE, "shutdown");
        }
    }

    /** Websocket listener: subscribes on open, reassembles fragments, reconnects on close/error. */
    private final class FeedListener implements WebSocket.Listener {

        private final StringBuilder buffer = new StringBuilder();

        @Override
        public void onOpen(WebSocket ws) {
            log.info("Market-data websocket connected");
            attempt.set(0);
            ws.sendText(buildSubscribeMessage(), true);
            ws.request(1);
        }

        @Override
        public CompletionStage<?> onText(WebSocket ws, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                String message = buffer.toString();
                buffer.setLength(0);
                handleMessage(message);
            }
            ws.request(1);
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket ws, int statusCode, String reason) {
            log.warn("Market-data websocket closed: {} {}", statusCode, reason);
            scheduleReconnect();
            return null;
        }

        @Override
        public void onError(WebSocket ws, Throwable error) {
            log.warn("Market-data websocket error: {}", error.toString());
            scheduleReconnect();
        }
    }
}
