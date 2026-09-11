package com.ledgerbull.position.service;

import com.ledgerbull.position.web.dto.IngestFillsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Periodically pulls fills from execution (which chains to recompute when new fills arrive)
 * so positions and the 5E post-trade monitor stay live without manual HTTP calls.
 */
@Component
public class FillIngestionScheduler {

    private static final Logger log = LoggerFactory.getLogger(FillIngestionScheduler.class);

    private final FillIngestionService fillIngestionService;

    public FillIngestionScheduler(FillIngestionService fillIngestionService) {
        this.fillIngestionService = fillIngestionService;
    }

    @Scheduled(fixedDelayString = "${ledgerbull.position.ingest-poll-ms:5000}")
    public void pollAndIngest() {
        try {
            IngestFillsResponse response = fillIngestionService.ingestFills();
            if (!response.executionReachable()) {
                log.warn("Scheduled fill ingest: execution unreachable; will retry next poll");
                return;
            }
            if (response.ingested() > 0) {
                log.info(
                        "Scheduled fill ingest: ingested={} seen={} duplicates={}",
                        response.ingested(),
                        response.seen(),
                        response.duplicates());
            }
            // Quiet when nothing changed — avoid log spam every 5s.
        } catch (RuntimeException ex) {
            log.warn("Scheduled fill ingest failed: {}", ex.getMessage());
        }
    }
}
