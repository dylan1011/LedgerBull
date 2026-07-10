package com.ledgerbull.execution.repository;

import com.ledgerbull.execution.entity.FillEntity;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FillRepository extends JpaRepository<FillEntity, Long> {

    List<FillEntity> findByOrderRefId(Long orderRefId);
}
