package com.ledgerbull.position.repository;

import com.ledgerbull.position.entity.LotEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LotRepository extends JpaRepository<LotEntity, Long> {

    List<LotEntity> findBySymbolAndRemainingQuantityGreaterThanOrderBySequenceNoAsc(String symbol, long remainingQuantity);

    List<LotEntity> findBySymbolOrderBySequenceNoAsc(String symbol);

    void deleteAllBySymbol(String symbol);
}
