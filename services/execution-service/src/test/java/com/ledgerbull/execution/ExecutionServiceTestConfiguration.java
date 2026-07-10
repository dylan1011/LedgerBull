package com.ledgerbull.execution;

import com.ledgerbull.execution.repository.OrderRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class ExecutionServiceTestConfiguration {

    @Bean
    @Primary
    OrderRepository orderRepository() {
        return Mockito.mock(OrderRepository.class);
    }
}
