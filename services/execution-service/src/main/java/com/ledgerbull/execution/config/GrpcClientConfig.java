package com.ledgerbull.execution.config;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;
import jakarta.annotation.PreDestroy;
import ledgerbull.api.MatchingEngineGrpc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class GrpcClientConfig {

    private ManagedChannel channel;

    @Bean
    MatchingEngineGrpc.MatchingEngineBlockingStub matchingEngineStub(MatchingEngineProperties props) {
        channel = ManagedChannelBuilder
                .forAddress(props.host(), props.port())
                .usePlaintext()
                .build();
        return MatchingEngineGrpc.newBlockingStub(channel);
    }

    @PreDestroy
    void shutdownChannel() {
        if (channel != null) {
            channel.shutdown();
        }
    }
}
