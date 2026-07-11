package com.ledgerbull.position.repository;

import com.ledgerbull.position.entity.PositionEntity;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PositionRepository extends JpaRepository<PositionEntity, Long> {

    Optional<PositionEntity> findBySymbol(String symbol);

    List<PositionEntity> findAllByOrderBySymbolAsc();
}
