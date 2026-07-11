package com.ledgerbull.execution.repository;

import com.ledgerbull.execution.entity.OrderEntity;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderId(String orderId);

    Page<OrderEntity> findBySymbol(String symbol, Pageable pageable);

    Page<OrderEntity> findByStatus(String status, Pageable pageable);

    Page<OrderEntity> findBySymbolAndStatus(String symbol, String status, Pageable pageable);
}
