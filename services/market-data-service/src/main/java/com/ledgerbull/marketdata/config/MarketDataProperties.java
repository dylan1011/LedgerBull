package com.ledgerbull.marketdata.config;

import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Binds the {@code marketdata.*} configuration:
 * the symbols to subscribe to and the upstream websocket feed URL.
 */
@ConfigurationProperties(prefix = "marketdata")
public class MarketDataProperties {

    /** Symbols/product ids to subscribe to, e.g. BTC-USD, ETH-USD. */
    private List<String> symbols = List.of("BTC-USD", "ETH-USD");

    /** Upstream market-data websocket feed URL. */
    private String websocketUrl = "wss://ws-feed.exchange.coinbase.com";

    public List<String> getSymbols() {
        return symbols;
    }

    public void setSymbols(List<String> symbols) {
        this.symbols = symbols;
    }

    public String getWebsocketUrl() {
        return websocketUrl;
    }

    public void setWebsocketUrl(String websocketUrl) {
        this.websocketUrl = websocketUrl;
    }
}
