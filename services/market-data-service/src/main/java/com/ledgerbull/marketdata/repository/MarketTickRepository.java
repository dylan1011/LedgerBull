package com.ledgerbull.marketdata.repository;

import com.ledgerbull.marketdata.domain.MarketTick;
import com.ledgerbull.marketdata.domain.MarketTickId;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface MarketTickRepository extends JpaRepository<MarketTick, MarketTickId> {

    /**
     * Recent ticks for a symbol within a time window, newest first. Parameterized query — no
     * string concatenation.
     */
    @Query("SELECT t FROM MarketTick t WHERE t.symbol = :symbol AND t.time >= :since ORDER BY t.time DESC")
    List<MarketTick> findRecent(@Param("symbol") String symbol, @Param("since") OffsetDateTime since);

    /**
     * Most recent tick for a symbol — the TimescaleDB (source-of-truth) fallback when the Redis
     * latest-price cache misses or Redis is unavailable. Derived, parameterized query.
     */
    Optional<MarketTick> findFirstBySymbolOrderByTimeDesc(String symbol);
}
