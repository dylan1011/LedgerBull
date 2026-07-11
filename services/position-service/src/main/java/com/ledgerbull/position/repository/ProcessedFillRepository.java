package com.ledgerbull.position.repository;

import com.ledgerbull.position.entity.ProcessedFillEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProcessedFillRepository extends JpaRepository<ProcessedFillEntity, Long> {

    boolean existsBySourceFillId(String sourceFillId);

    List<ProcessedFillEntity> findBySymbol(String symbol);

    List<ProcessedFillEntity> findAllByOrderByIngestedAtDesc();
}
