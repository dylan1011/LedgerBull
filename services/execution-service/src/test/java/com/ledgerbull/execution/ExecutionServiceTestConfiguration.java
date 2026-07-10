package com.ledgerbull.execution;

import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.FillRepository;
import com.ledgerbull.execution.repository.OrderRepository;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ExecutionServiceTestConfiguration {

    @Bean
    @Primary
    OrderRepository orderRepository() {
        OrderRepository repository = Mockito.mock(OrderRepository.class);
        when(repository.save(any(OrderEntity.class))).thenAnswer(invocation -> invocation.getArgument(0));
        return repository;
    }

    @Bean
    @Primary
    FillRepository fillRepository() {
        return Mockito.mock(FillRepository.class);
    }
}
