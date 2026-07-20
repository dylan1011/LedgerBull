package com.ledgerbull.position.web;

import com.ledgerbull.position.entity.ProcessedFillEntity;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import com.ledgerbull.position.service.FillIngestionService;
import com.ledgerbull.position.service.PositionService;
import com.ledgerbull.position.web.dto.IngestFillsResponse;
import com.ledgerbull.position.web.dto.LotResponse;
import com.ledgerbull.position.web.dto.PositionSummaryResponse;
import com.ledgerbull.position.web.dto.ProcessedFillResponse;
import com.ledgerbull.position.web.dto.RecomputePositionsResponse;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/positions")
public class PositionController {

    private final FillIngestionService fillIngestionService;
    private final PositionService positionService;
    private final ProcessedFillRepository processedFillRepository;

    public PositionController(
            FillIngestionService fillIngestionService,
            PositionService positionService,
            ProcessedFillRepository processedFillRepository) {
        this.fillIngestionService = fillIngestionService;
        this.positionService = positionService;
        this.processedFillRepository = processedFillRepository;
    }

    @PostMapping("/ingest-fills")
    public ResponseEntity<IngestFillsResponse> ingestFills() {
        return ResponseEntity.ok(fillIngestionService.ingestFills());
    }

    @PostMapping("/recompute")
    public ResponseEntity<RecomputePositionsResponse> recomputePositions() {
        return ResponseEntity.ok(positionService.recomputePositions());
    }

    @GetMapping
    public ResponseEntity<List<PositionSummaryResponse>> listPositions() {
        return ResponseEntity.ok(positionService.listPositions());
    }

    @GetMapping("/{symbol}")
    public ResponseEntity<PositionSummaryResponse> getPosition(@PathVariable String symbol) {
        return positionService
                .getPosition(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{symbol}/lots")
    public ResponseEntity<List<LotResponse>> listLots(@PathVariable String symbol) {
        return ResponseEntity.ok(positionService.listLots(symbol));
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
                entity.getTakerSide(),
                entity.getIngestedAt().toString());
    }
}
