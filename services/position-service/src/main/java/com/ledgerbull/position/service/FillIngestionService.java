package com.ledgerbull.position.service;

import com.ledgerbull.position.client.ExecutionClient;
import com.ledgerbull.position.client.ExecutionFillsFetchResult;
import com.ledgerbull.position.web.dto.IngestFillsResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class FillIngestionService {

    private static final Logger log = LoggerFactory.getLogger(FillIngestionService.class);

    private final ExecutionClient executionClient;
    private final ProcessedFillWriter processedFillWriter;
    private final PositionService positionService;

    public FillIngestionService(
            ExecutionClient executionClient,
            ProcessedFillWriter processedFillWriter,
            PositionService positionService) {
        this.executionClient = executionClient;
        this.processedFillWriter = processedFillWriter;
        this.positionService = positionService;
    }

    public IngestFillsResponse ingestFills() {
        ExecutionFillsFetchResult fetchResult = executionClient.fetchAllFills();
        if (!fetchResult.executionReachable()) {
            log.warn("Fill ingestion skipped: execution service unreachable");
            return new IngestFillsResponse(0, 0, 0, false);
        }
        IngestFillsResponse response = processedFillWriter.persistNewFills(fetchResult.fills());
        // Chain to recompute so positions + 5E post-trade monitor stay current after new fills.
        // No circular dependency: PositionService does not depend on FillIngestionService.
        if (response.ingested() > 0) {
            log.info(
                    "Ingested {} new fill(s) (seen={}, duplicates={}); recomputing positions",
                    response.ingested(),
                    response.seen(),
                    response.duplicates());
            positionService.recomputePositions();
        }
        return response;
    }
}
