package com.ledgerbull.position.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "execution")
public record ExecutionProperties(String host, int port) {

    public String baseUrl() {
        return "http://" + host + ":" + port;
    }
}
