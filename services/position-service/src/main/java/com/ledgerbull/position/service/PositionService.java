package com.ledgerbull.position.service;

import com.ledgerbull.position.entity.PositionEntity;
import com.ledgerbull.position.entity.ProcessedFillEntity;
import com.ledgerbull.position.repository.PositionRepository;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import com.ledgerbull.position.web.dto.PositionSummaryResponse;
import com.ledgerbull.position.web.dto.RecomputePositionsResponse;
import com.ledgerbull.position.web.dto.SymbolNetQuantityResponse;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PositionService {

    private static final Logger log = LoggerFactory.getLogger(PositionService.class);
    private static final String BUY = "BUY";
    private static final String SELL = "SELL";

    private final ProcessedFillRepository processedFillRepository;
    private final PositionRepository positionRepository;

    public PositionService(ProcessedFillRepository processedFillRepository, PositionRepository positionRepository) {
        this.processedFillRepository = processedFillRepository;
        this.positionRepository = positionRepository;
    }

    @Transactional
    public RecomputePositionsResponse recomputePositions() {
        Map<String, Long> netBySymbol = computeNetQuantitiesFromFills(processedFillRepository.findAll());
        List<SymbolNetQuantityResponse> updated = new ArrayList<>();

        for (Map.Entry<String, Long> entry : netBySymbol.entrySet()) {
            String symbol = entry.getKey();
            long netQuantity = entry.getValue();

            PositionEntity position = positionRepository
                    .findBySymbol(symbol)
                    .orElseGet(PositionEntity::new);
            position.setSymbol(symbol);
            position.setNetQuantity(netQuantity);
            position.setRealizedPnl(0L);
            positionRepository.save(position);

            updated.add(new SymbolNetQuantityResponse(symbol, netQuantity));
        }

        updated.sort((a, b) -> a.symbol().compareTo(b.symbol()));
        return new RecomputePositionsResponse(updated.size(), updated);
    }

    public List<PositionSummaryResponse> listPositions() {
        return positionRepository.findAllByOrderBySymbolAsc().stream()
                .map(position -> new PositionSummaryResponse(
                        position.getSymbol(),
                        position.getNetQuantity(),
                        position.getRealizedPnl()))
                .toList();
    }

    static Map<String, Long> computeNetQuantitiesFromFills(List<ProcessedFillEntity> fills) {
        Map<String, Long> netBySymbol = new HashMap<>();
        for (ProcessedFillEntity fill : fills) {
            String takerSide = fill.getTakerSide();
            if (takerSide == null || takerSide.isBlank()) {
                continue;
            }
            long delta = switch (takerSide.trim().toUpperCase()) {
                case BUY -> fill.getFillQuantity();
                case SELL -> -fill.getFillQuantity();
                default -> {
                    log.warn("Skipping fill {} with unknown taker_side: {}", fill.getSourceFillId(), takerSide);
                    yield 0L;
                }
            };
            if (delta != 0L) {
                netBySymbol.merge(fill.getSymbol(), delta, Long::sum);
            }
        }
        return netBySymbol;
    }
}
