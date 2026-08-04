package com.ledgerbull.position.repository;

import com.ledgerbull.position.entity.RiskEventEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RiskEventRepository extends JpaRepository<RiskEventEntity, Long> {

    List<RiskEventEntity> findBySymbolOrderByCreatedAtDesc(String symbol);

    List<RiskEventEntity> findByEventTypeOrderByCreatedAtDesc(String eventType);

    List<RiskEventEntity> findTop50ByOrderByCreatedAtDesc();

    Optional<RiskEventEntity> findTopBySymbolAndEventTypeOrderByCreatedAtDesc(String symbol, String eventType);
}
