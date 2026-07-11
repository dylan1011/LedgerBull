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

    public FillIngestionService(ExecutionClient executionClient, ProcessedFillWriter processedFillWriter) {
        this.executionClient = executionClient;
        this.processedFillWriter = processedFillWriter;
    }

    public IngestFillsResponse ingestFills() {
        ExecutionFillsFetchResult fetchResult = executionClient.fetchAllFills();
        if (!fetchResult.executionReachable()) {
            log.warn("Fill ingestion skipped: execution service unreachable");
            return new IngestFillsResponse(0, 0, 0, false);
        }
        return processedFillWriter.persistNewFills(fetchResult.fills());
    }
}
