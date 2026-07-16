package com.ledgerbull.position.service;

import com.ledgerbull.position.entity.LotEntity;
import com.ledgerbull.position.entity.PositionEntity;
import com.ledgerbull.position.entity.ProcessedFillEntity;
import com.ledgerbull.position.money.Money;
import com.ledgerbull.position.repository.LotRepository;
import com.ledgerbull.position.repository.PositionRepository;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import com.ledgerbull.position.web.dto.LotResponse;
import com.ledgerbull.position.web.dto.PositionSummaryResponse;
import com.ledgerbull.position.web.dto.RecomputePositionsResponse;
import com.ledgerbull.position.web.dto.SymbolPositionResponse;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
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
    private final LotRepository lotRepository;

    public PositionService(
            ProcessedFillRepository processedFillRepository,
            PositionRepository positionRepository,
            LotRepository lotRepository) {
        this.processedFillRepository = processedFillRepository;
        this.positionRepository = positionRepository;
        this.lotRepository = lotRepository;
    }

    /**
     * Full recompute: clears lots, resets realized PnL, replays all processed fills chronologically
     * to rebuild net_quantity, FIFO lots, and realized_pnl. Idempotent.
     */
    @Transactional
    public RecomputePositionsResponse recomputePositions() {
        lotRepository.deleteAllInBatch();

        List<ProcessedFillEntity> fills = processedFillRepository.findAll();
        fills.sort(Comparator
                .comparing(ProcessedFillEntity::getFillCreatedAt, Comparator.nullsLast(Comparator.naturalOrder()))
                .thenComparing(ProcessedFillEntity::getId, Comparator.nullsLast(Comparator.naturalOrder())));
        Map<String, List<LotEntity>> openLotsBySymbol = new HashMap<>();
        Map<String, Long> netBySymbol = new HashMap<>();
        Map<String, Long> realizedBySymbol = new HashMap<>();
        List<LotEntity> allLots = new ArrayList<>();
        long nextSequenceNo = 1L;

        for (ProcessedFillEntity fill : fills) {
            String takerSide = fill.getTakerSide();
            if (takerSide == null || takerSide.isBlank()) {
                continue;
            }
            String side = takerSide.trim().toUpperCase();
            String symbol = fill.getSymbol();
            long quantity = fill.getFillQuantity();
            long priceTicks = fill.getFillPrice();

            if (BUY.equals(side)) {
                LotEntity lot = new LotEntity();
                lot.setSymbol(symbol);
                lot.setSide(BUY);
                lot.setOriginalQuantity(quantity);
                lot.setRemainingQuantity(quantity);
                lot.setPrice(priceTicks);
                lot.setSequenceNo(nextSequenceNo++);
                lot.setSourceFillId(fill.getSourceFillId());
                allLots.add(lot);
                openLotsBySymbol.computeIfAbsent(symbol, key -> new ArrayList<>()).add(lot);
                netBySymbol.merge(symbol, quantity, Long::sum);
                realizedBySymbol.putIfAbsent(symbol, 0L);
            } else if (SELL.equals(side)) {
                long sellRealized = consumeLotsFifo(
                        openLotsBySymbol.computeIfAbsent(symbol, key -> new ArrayList<>()),
                        symbol,
                        quantity,
                        priceTicks);
                realizedBySymbol.merge(symbol, sellRealized, Long::sum);
                netBySymbol.merge(symbol, -quantity, Long::sum);
            } else {
                log.warn("Skipping fill {} with unknown taker_side: {}", fill.getSourceFillId(), takerSide);
            }
        }

        lotRepository.saveAll(allLots);

        Set<String> symbols = new TreeSet<>();
        symbols.addAll(netBySymbol.keySet());
        symbols.addAll(realizedBySymbol.keySet());

        // Also reset any leftover position rows whose fills were cleared but positions remain.
        for (PositionEntity existing : positionRepository.findAll()) {
            symbols.add(existing.getSymbol());
        }

        List<SymbolPositionResponse> updated = new ArrayList<>();
        for (String symbol : symbols) {
            long netQuantity = netBySymbol.getOrDefault(symbol, 0L);
            long realizedPnl = realizedBySymbol.getOrDefault(symbol, 0L);

            PositionEntity position = positionRepository
                    .findBySymbol(symbol)
                    .orElseGet(PositionEntity::new);
            position.setSymbol(symbol);
            position.setNetQuantity(netQuantity);
            position.setRealizedPnl(realizedPnl);
            positionRepository.save(position);

            updated.add(new SymbolPositionResponse(symbol, netQuantity, realizedPnl, Money.toHuman(realizedPnl)));
        }

        return new RecomputePositionsResponse(updated.size(), updated);
    }

    /**
     * Consumes oldest open lots first for a SELL. Returns realized PnL in ticks for matched quantity.
     * Oversell leftover is logged and skipped (long-only — no short lots).
     */
    private static long consumeLotsFifo(List<LotEntity> openLots, String symbol, long qtyToSell, long sellPrice) {
        long remainingToSell = qtyToSell;
        long realized = 0L;

        for (LotEntity lot : openLots) {
            if (remainingToSell == 0L) {
                break;
            }
            long lotRemaining = lot.getRemainingQuantity();
            if (lotRemaining <= 0L) {
                continue;
            }
            long take = Math.min(remainingToSell, lotRemaining);
            long priceDiffTicks = sellPrice - lot.getPrice();
            realized += Money.multiplyQtyPrice(take, priceDiffTicks);
            lot.setRemainingQuantity(lotRemaining - take);
            remainingToSell -= take;
        }

        if (remainingToSell > 0L) {
            log.warn(
                    "oversell on {}: {} units had no matching lot, skipped for PnL",
                    symbol,
                    remainingToSell);
        }
        return realized;
    }

    public List<PositionSummaryResponse> listPositions() {
        return positionRepository.findAllByOrderBySymbolAsc().stream()
                .map(position -> new PositionSummaryResponse(
                        position.getSymbol(),
                        position.getNetQuantity(),
                        position.getRealizedPnl(),
                        Money.toHuman(position.getRealizedPnl())))
                .toList();
    }

    public List<LotResponse> listLots(String symbol) {
        return lotRepository.findBySymbolOrderBySequenceNoAsc(symbol).stream()
                .map(lot -> new LotResponse(
                        lot.getSymbol(),
                        lot.getSide(),
                        lot.getOriginalQuantity(),
                        lot.getRemainingQuantity(),
                        lot.getPrice(),
                        Money.toHuman(lot.getPrice()),
                        lot.getSequenceNo(),
                        lot.getSourceFillId()))
                .toList();
    }
}
