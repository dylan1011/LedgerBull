package com.ledgerbull.marketdata.ingest;

import com.fasterxml.jackson.databind.JsonNode;
import com.ledgerbull.marketdata.domain.MarketTick;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps a raw Coinbase {@code ticker} message into the internal {@link MarketTick} format.
 * Non-ticker messages (subscriptions, heartbeats, errors) yield {@link Optional#empty()}.
 */
@Component
public class TickNormalizer {

    private static final Logger log = LoggerFactory.getLogger(TickNormalizer.class);
    private static final String SOURCE = "coinbase";

    public Optional<MarketTick> normalize(JsonNode node) {
        String type = node.path("type").asText("");
        if (!"ticker".equals(type)) {
            return Optional.empty();
        }
        String symbol = node.path("product_id").asText(null);
        JsonNode priceNode = node.path("price");
        if (symbol == null || priceNode.isMissingNode() || priceNode.isNull()) {
            return Optional.empty();
        }
        try {
            double price = Double.parseDouble(priceNode.asText());
            Double volume = parseNullableDouble(node.path("last_size"));
            OffsetDateTime time = node.hasNonNull("time")
                    ? OffsetDateTime.parse(node.get("time").asText())
                    : OffsetDateTime.now();
            return Optional.of(new MarketTick(time, symbol, price, volume, SOURCE));
        } catch (RuntimeException ex) {
            log.warn("Failed to normalize ticker message: {}", node, ex);
            return Optional.empty();
        }
    }

    private Double parseNullableDouble(JsonNode node) {
        if (node == null || node.isMissingNode() || node.isNull()) {
            return null;
        }
        String text = node.asText("");
        if (text.isBlank()) {
            return null;
        }
        try {
            return Double.parseDouble(text);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
