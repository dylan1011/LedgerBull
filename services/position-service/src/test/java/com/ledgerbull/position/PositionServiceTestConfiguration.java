package com.ledgerbull.position;

import com.ledgerbull.position.client.ExecutionClient;
import com.ledgerbull.position.repository.PositionRepository;
import com.ledgerbull.position.repository.ProcessedFillRepository;
import org.mockito.Mockito;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;

@TestConfiguration
public class PositionServiceTestConfiguration {

    @Bean
    @Primary
    ProcessedFillRepository processedFillRepository() {
        return Mockito.mock(ProcessedFillRepository.class);
    }

    @Bean
    @Primary
    PositionRepository positionRepository() {
        return Mockito.mock(PositionRepository.class);
    }

    @Bean
    @Primary
    ExecutionClient executionClient() {
        return Mockito.mock(ExecutionClient.class);
    }
}
