package com.ledgerbull.marketdata.service;

import com.ledgerbull.marketdata.domain.MarketTick;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.BatchPreparedStatementSetter;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Buffers incoming ticks and writes them to TimescaleDB in batches, to survive high feed volume
 * without one DB round-trip per tick.
 *
 * <p>Backpressure protection: the buffer is <b>bounded</b>. If ticks arrive faster than they can be
 * flushed, the <b>oldest</b> buffered ticks are dropped (for market data the newest prices matter
 * most), so memory never grows without bound.
 *
 * <p>Fault tolerance: a failed batch insert is logged and dropped rather than propagated, so a
 * TimescaleDB hiccup never kills the ingestion path — writes resume automatically once the DB is
 * reachable again. All writes are parameterized (batch prepared statement), never concatenated SQL.
 */
@Component
public class TickBatchWriter {

    private static final Logger log = LoggerFactory.getLogger(TickBatchWriter.class);
    private static final String INSERT_SQL =
            "INSERT INTO market_ticks (time, symbol, price, volume, source) VALUES (?, ?, ?, ?, ?)";

    private final JdbcTemplate jdbcTemplate;
    private final int batchSize;
    private final long flushIntervalMs;
    private final LinkedBlockingDeque<MarketTick> buffer;

    private final ScheduledExecutorService flusher =
            Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "marketdata-batch-writer");
                t.setDaemon(true);
                return t;
            });

    private final AtomicLong droppedCount = new AtomicLong();
    private final AtomicLong failedCount = new AtomicLong();

    public TickBatchWriter(JdbcTemplate jdbcTemplate,
            @Value("${marketdata.batch.size:100}") int batchSize,
            @Value("${marketdata.batch.flush-interval-ms:1000}") long flushIntervalMs,
            @Value("${marketdata.batch.max-buffer:10000}") int maxBufferSize) {
        this.jdbcTemplate = jdbcTemplate;
        this.batchSize = batchSize;
        this.flushIntervalMs = flushIntervalMs;
        this.buffer = new LinkedBlockingDeque<>(maxBufferSize);
    }

    @PostConstruct
    public void start() {
        log.info("Starting tick batch writer (batchSize={}, flushIntervalMs={}, maxBuffer={})",
                batchSize, flushIntervalMs, buffer.remainingCapacity());
        flusher.scheduleWithFixedDelay(this::flushQuietly, flushIntervalMs, flushIntervalMs,
                TimeUnit.MILLISECONDS);
    }

    /**
     * Enqueue a tick for batched persistence. Non-blocking: if the bounded buffer is full, the
     * oldest tick is evicted to make room. Triggers an early flush once a full batch has accumulated.
     */
    public void submit(MarketTick tick) {
        while (!buffer.offerLast(tick)) {
            if (buffer.pollFirst() != null) {
                long dropped = droppedCount.incrementAndGet();
                if (dropped % 1000 == 1) {
                    log.warn("Tick buffer full — dropping oldest ticks (total dropped: {})", dropped);
                }
            }
        }
        if (buffer.size() >= batchSize) {
            flusher.submit(this::flushQuietly);
        }
    }

    private void flushQuietly() {
        try {
            flush();
        } catch (RuntimeException ex) {
            log.error("Unexpected error while flushing tick batch", ex);
        }
    }

    /** Drain and persist all currently buffered ticks in batches. */
    private void flush() {
        List<MarketTick> batch = new ArrayList<>(batchSize);
        while (buffer.drainTo(batch, batchSize) > 0) {
            try {
                persistBatch(batch);
            } catch (DataAccessException ex) {
                long failed = failedCount.addAndGet(batch.size());
                log.error("Failed to persist batch of {} ticks to TimescaleDB; dropping this batch "
                        + "(will resume when DB recovers, total failed: {})", batch.size(), failed, ex);
            }
            batch.clear();
        }
    }

    private void persistBatch(List<MarketTick> batch) {
        jdbcTemplate.batchUpdate(INSERT_SQL, new BatchPreparedStatementSetter() {
            @Override
            public void setValues(PreparedStatement ps, int i) throws SQLException {
                MarketTick tick = batch.get(i);
                ps.setObject(1, tick.getTime());
                ps.setString(2, tick.getSymbol());
                ps.setDouble(3, tick.getPrice());
                if (tick.getVolume() != null) {
                    ps.setDouble(4, tick.getVolume());
                } else {
                    ps.setNull(4, Types.DOUBLE);
                }
                ps.setString(5, tick.getSource());
            }

            @Override
            public int getBatchSize() {
                return batch.size();
            }
        });
    }

    @PreDestroy
    public void shutdown() {
        flusher.shutdown();
        try {
            if (!flusher.awaitTermination(5, TimeUnit.SECONDS)) {
                flusher.shutdownNow();
            }
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            flusher.shutdownNow();
        }
        // Best-effort final drain so buffered ticks aren't lost on graceful shutdown.
        flushQuietly();
    }
}
