package com.ledgerbull.position.web;

import com.ledgerbull.position.entity.ProcessedFillEntity;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import com.ledgerbull.position.service.FillIngestionService;
import com.ledgerbull.position.web.dto.IngestFillsResponse;
import com.ledgerbull.position.web.dto.ProcessedFillResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final FillIngestionService fillIngestionService;
    private final ProcessedFillRepository processedFillRepository;

    public PositionController(
            FillIngestionService fillIngestionService, ProcessedFillRepository processedFillRepository) {
        this.fillIngestionService = fillIngestionService;
        this.processedFillRepository = processedFillRepository;
    }

    @PostMapping("/ingest-fills")
    public ResponseEntity<IngestFillsResponse> ingestFills() {
        return ResponseEntity.ok(fillIngestionService.ingestFills());
    }

    @GetMapping("/processed-fills")
    public ResponseEntity<List<ProcessedFillResponse>> listProcessedFills(
            @RequestParam(required = false) String symbol) {
        List<ProcessedFillEntity> fills = symbol == null || symbol.isBlank()
                ? processedFillRepository.findAllByOrderByIngestedAtDesc()
                : processedFillRepository.findBySymbol(symbol.trim());
        return ResponseEntity.ok(fills.stream().map(this::toResponse).toList());
    }

    private ProcessedFillResponse toResponse(ProcessedFillEntity entity) {
        return new ProcessedFillResponse(
                entity.getSourceFillId(),
                entity.getTakerOrderId(),
                entity.getMakerOrderId(),
                entity.getSymbol(),
                entity.getFillPrice(),
                entity.getFillQuantity(),
                entity.getIngestedAt().toString());
    }
}
