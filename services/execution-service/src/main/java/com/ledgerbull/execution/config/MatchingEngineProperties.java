package com.ledgerbull.execution.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "matching-engine")
public record MatchingEngineProperties(String host, int port) {
}
