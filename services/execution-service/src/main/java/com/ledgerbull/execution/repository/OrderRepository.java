package com.ledgerbull.execution.repository;

import com.ledgerbull.execution.entity.OrderEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderRepository extends JpaRepository<OrderEntity, Long> {

    Optional<OrderEntity> findByOrderId(String orderId);
}
