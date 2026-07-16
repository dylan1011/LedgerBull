package com.ledgerbull.position.service;

import com.ledgerbull.position.client.dto.ExecutionIngestFill;
import com.ledgerbull.position.entity.ProcessedFillEntity;
import com.ledgerbull.position.money.Money;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import com.ledgerbull.position.web.dto.IngestFillsResponse;
import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ProcessedFillWriter {

    private static final Logger log = LoggerFactory.getLogger(ProcessedFillWriter.class);

    private final ProcessedFillRepository processedFillRepository;

    public ProcessedFillWriter(ProcessedFillRepository processedFillRepository) {
        this.processedFillRepository = processedFillRepository;
    }

    @Transactional
    public IngestFillsResponse persistNewFills(List<ExecutionIngestFill> fills) {
        int seen = 0;
        int ingested = 0;
        int duplicates = 0;
        Set<String> seenKeys = new HashSet<>();

        for (ExecutionIngestFill fill : fills) {
            long fillPriceTicks = Money.toTicks(fill.price());
            String sourceFillId = FillIdempotencyKey.fromComposite(
                    fill.taker_order_id(),
                    fill.maker_order_id(),
                    fill.symbol(),
                    fillPriceTicks,
                    fill.quantity());

            if (!seenKeys.add(sourceFillId)) {
                continue;
            }
            seen++;

            if (processedFillRepository.existsBySourceFillId(sourceFillId)) {
                duplicates++;
                continue;
            }

            ProcessedFillEntity entity = new ProcessedFillEntity();
            entity.setSourceFillId(sourceFillId);
            entity.setTakerOrderId(fill.taker_order_id());
            entity.setMakerOrderId(fill.maker_order_id());
            entity.setSymbol(fill.symbol());
            entity.setFillPrice(fillPriceTicks);
            entity.setFillQuantity(fill.quantity());
            entity.setTakerSide(fill.taker_side());
            entity.setFillCreatedAt(parseFillCreatedAt(fill.created_at()));

            try {
                processedFillRepository.save(entity);
                ingested++;
            } catch (DataIntegrityViolationException ex) {
                duplicates++;
                log.debug("Duplicate fill skipped via UNIQUE constraint: {}", sourceFillId);
            }
        }

        return new IngestFillsResponse(seen, ingested, duplicates, true);
    }

    private static OffsetDateTime parseFillCreatedAt(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return OffsetDateTime.parse(raw);
        } catch (DateTimeParseException ex) {
            log.warn("Could not parse fill created_at '{}': {}", raw, ex.getMessage());
            return null;
        }
    }
}
