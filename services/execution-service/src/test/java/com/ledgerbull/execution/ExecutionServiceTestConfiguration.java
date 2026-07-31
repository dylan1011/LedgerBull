package com.ledgerbull.execution;

import com.ledgerbull.execution.entity.OrderEntity;
import com.ledgerbull.execution.repository.FillRepository;
import com.ledgerbull.execution.repository.OrderRepository;
import com.ledgerbull.execution.risk.RiskDecision;
import com.ledgerbull.execution.risk.RiskEngine;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.when;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.data.redis.core.StringRedisTemplate;

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

    /** Redis excluded in tests; provide a stub so MarketPriceClient can wire. */
    @Bean
    @Primary
    StringRedisTemplate stringRedisTemplate() {
        return Mockito.mock(StringRedisTemplate.class);
    }

    /**
     * Integration tests exercise validation/engine paths; allow all risk checks so behavior
     * matches pre-5B unless a test overrides this bean.
     */
    @Bean
    @Primary
    RiskEngine riskEngine() {
        RiskEngine engine = Mockito.mock(RiskEngine.class);
        when(engine.evaluateNewOrder(
                        anyString(), anyString(), anyString(), nullable(Double.class), anyLong()))
                .thenReturn(RiskDecision.allow());
        return engine;
    }
}
