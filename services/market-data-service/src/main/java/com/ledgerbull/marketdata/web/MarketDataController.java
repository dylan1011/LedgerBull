package com.ledgerbull.marketdata.web;

import com.ledgerbull.marketdata.service.MarketTickService;
import com.ledgerbull.marketdata.web.dto.LatestPriceResponse;
import com.ledgerbull.marketdata.web.dto.TickResponse;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/market-data")
@Validated
public class MarketDataController {

    private final MarketTickService tickService;

    public MarketDataController(MarketTickService tickService) {
        this.tickService = tickService;
    }

    /**
     * Latest price for a symbol: Redis fast path, falling back to TimescaleDB when the cache misses
     * or Redis is down. 404 only if the symbol has never been seen.
     */
    @GetMapping("/latest/{symbol}")
    public ResponseEntity<LatestPriceResponse> latest(@PathVariable String symbol) {
        return tickService.latestPrice(symbol)
                .map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /** Recent ticks for a symbol over the last {@code minutes} (default 5), newest first. */
    @GetMapping("/history/{symbol}")
    public List<TickResponse> history(
            @PathVariable String symbol,
            @RequestParam(defaultValue = "5") @Min(1) @Max(1440) int minutes) {
        return tickService.history(symbol, minutes);
    }
}
