#include "ledgerbull/order_book.hpp"
#include "test_framework.hpp"

#include <cstdint>
#include <numeric>

using ledgerbull::Fill;
using ledgerbull::Order;
using ledgerbull::OrderBook;
using ledgerbull::OrderType;
using ledgerbull::Side;

namespace {

Order limit_buy(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::BUY, price, qty, OrderType::LIMIT);
}

Order limit_sell(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::SELL, price, qty, OrderType::LIMIT);
}

Order market_buy(std::uint64_t id, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::BUY, 0, qty, OrderType::MARKET);
}

Order market_sell(std::uint64_t id, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::SELL, 0, qty, OrderType::MARKET);
}

std::int64_t fill_qty_sum(const std::vector<Fill>& fills) {
    return std::accumulate(fills.begin(), fills.end(), static_cast<std::int64_t>(0),
                           [](std::int64_t acc, const Fill& f) { return acc + f.quantity; });
}

void check_fill(const Fill& f, std::uint64_t taker, std::uint64_t maker, std::int64_t price,
                std::int64_t qty) {
    CHECK_EQ(f.taker_order_id, taker);
    CHECK_EQ(f.maker_order_id, maker);
    CHECK_EQ(f.price, price);
    CHECK_EQ(f.quantity, qty);
}

}  // namespace

// 1. No cross -> rests.
TEST_CASE(no_cross_limit_buy_rests) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 10));

    auto fills = book.submit_order(limit_buy(2, 100, 5));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(0));
    CHECK_EQ(book.size(), static_cast<std::size_t>(2));
    CHECK(book.best_bid().has_value());
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(100));
    CHECK_EQ(book.best_bid()->order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(book.best_ask()->price, static_cast<std::int64_t>(105));
}

// 2. Exact match -> book empty afterward.
TEST_CASE(exact_match_empties_book) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 10));

    auto fills = book.submit_order(limit_buy(2, 105, 10));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 105, 10);
    CHECK(book.empty());
    CHECK(!book.best_bid().has_value());
    CHECK(!book.best_ask().has_value());
}

// 3. Partial fill of resting order.
TEST_CASE(partial_fill_of_resting_ask) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 10));

    auto fills = book.submit_order(limit_buy(2, 105, 4));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 105, 4);
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_ask()->quantity, static_cast<std::int64_t>(6));
    CHECK(!book.best_bid().has_value());
}

// 4. Partial fill of incoming -> remainder rests as bid.
TEST_CASE(partial_fill_of_incoming_rests_remainder) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 4));

    auto fills = book.submit_order(limit_buy(2, 105, 10));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 105, 4);
    CHECK(!book.best_ask().has_value());
    CHECK(book.best_bid().has_value());
    CHECK_EQ(book.best_bid()->order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(105));
    CHECK_EQ(book.best_bid()->quantity, static_cast<std::int64_t>(6));
}

// 5. Sweep multiple price levels.
TEST_CASE(sweep_multiple_ask_levels) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 5));
    book.add_order(limit_sell(2, 106, 5));

    auto fills = book.submit_order(limit_buy(3, 106, 8));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(2));
    check_fill(fills[0], 3, 1, 105, 5);
    check_fill(fills[1], 3, 2, 106, 3);
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_ask()->order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(book.best_ask()->quantity, static_cast<std::int64_t>(2));
    CHECK(!book.best_bid().has_value());
}

// 6. Price-time priority at same level (FIFO).
TEST_CASE(time_priority_at_same_ask_level) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(10, 105, 5));  // seq 1
    book.add_order(limit_sell(20, 105, 5));  // seq 2

    auto fills = book.submit_order(limit_buy(30, 105, 5));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 30, 10, 105, 5);
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_ask()->order_id, static_cast<std::uint64_t>(20));
}

// 7. Trade price = maker (resting) price, not taker limit.
TEST_CASE(trade_price_is_maker_price) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 103, 5));

    auto fills = book.submit_order(limit_buy(2, 105, 5));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 103, 5);  // 103, not 105
}

// 8a. Market order fully filled.
TEST_CASE(market_buy_fully_filled) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 5));
    book.add_order(limit_sell(2, 106, 5));

    auto fills = book.submit_order(market_buy(3, 7));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(2));
    check_fill(fills[0], 3, 1, 105, 5);
    check_fill(fills[1], 3, 2, 106, 2);
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_ask()->quantity, static_cast<std::int64_t>(3));
    CHECK(!book.best_bid().has_value());  // market remainder not rested
}

// 8b. Market order remainder discarded when book side exhausted.
TEST_CASE(market_buy_remainder_discarded) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 105, 3));

    auto fills = book.submit_order(market_buy(2, 10));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 105, 3);
    CHECK(book.empty());
    CHECK(!book.best_bid().has_value());  // 7-unit remainder discarded, not rested
}

// 9. Quantity conservation across multi-fill match.
TEST_CASE(quantity_conservation_multi_fill) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 100, 3));
    book.add_order(limit_sell(2, 101, 4));
    book.add_order(limit_sell(3, 102, 5));

    const std::int64_t incoming_qty = 9;
    auto fills = book.submit_order(limit_buy(4, 102, incoming_qty));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(3));
    CHECK_EQ(fill_qty_sum(fills), incoming_qty);

    std::int64_t maker_reduction = 0;
    for (const Fill& f : fills) {
        maker_reduction += f.quantity;
        // Never execute worse than the taker's limit price.
        CHECK(f.price <= static_cast<std::int64_t>(102));
    }
    CHECK_EQ(maker_reduction, incoming_qty);
}

// 10a. Sell side: incoming sell crosses resting bids.
TEST_CASE(sell_crosses_bids_exact_match) {
    OrderBook book("BTC-USD");
    book.add_order(limit_buy(1, 100, 8));

    auto fills = book.submit_order(limit_sell(2, 100, 8));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(1));
    check_fill(fills[0], 2, 1, 100, 8);
    CHECK(book.empty());
}

// 10b. Sell side: sweep multiple bid levels.
TEST_CASE(sell_sweeps_multiple_bid_levels) {
    OrderBook book("BTC-USD");
    book.add_order(limit_buy(1, 100, 5));
    book.add_order(limit_buy(2, 99, 5));

    auto fills = book.submit_order(limit_sell(3, 99, 7));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(2));
    check_fill(fills[0], 3, 1, 100, 5);  // best bid first
    check_fill(fills[1], 3, 2, 99, 2);
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_bid()->order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(book.best_bid()->quantity, static_cast<std::int64_t>(3));
}

// Never match a buy at a price worse than its limit.
TEST_CASE(limit_buy_never_pays_above_limit) {
    OrderBook book("BTC-USD");
    book.add_order(limit_sell(1, 106, 5));

    auto fills = book.submit_order(limit_buy(2, 105, 5));
    CHECK_EQ(fills.size(), static_cast<std::size_t>(0));
    CHECK(book.best_bid().has_value());
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(105));
}
