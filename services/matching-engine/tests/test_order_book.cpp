#include "ledgerbull/order_book.hpp"
#include "test_framework.hpp"

#include <cstdint>

using ledgerbull::Order;
using ledgerbull::OrderBook;
using ledgerbull::Side;

namespace {

Order buy(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::BUY, price, qty);
}

Order sell(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::SELL, price, qty);
}

}  // namespace

// 1. Bids order highest-price-first.
TEST_CASE(bids_ordered_highest_price_first) {
    OrderBook book("BTC-USD");
    book.add_order(buy(1, 100, 5));
    book.add_order(buy(2, 102, 5));
    book.add_order(buy(3, 101, 5));

    auto bids = book.get_bids();
    CHECK_EQ(bids.size(), static_cast<std::size_t>(3));
    CHECK_EQ(bids[0].price, static_cast<std::int64_t>(102));
    CHECK_EQ(bids[1].price, static_cast<std::int64_t>(101));
    CHECK_EQ(bids[2].price, static_cast<std::int64_t>(100));

    auto best = book.best_bid();
    CHECK(best.has_value());
    CHECK_EQ(best->price, static_cast<std::int64_t>(102));
    CHECK_EQ(best->order_id, static_cast<std::uint64_t>(2));
}

// 2. Asks order lowest-price-first.
TEST_CASE(asks_ordered_lowest_price_first) {
    OrderBook book("BTC-USD");
    book.add_order(sell(1, 105, 5));
    book.add_order(sell(2, 103, 5));
    book.add_order(sell(3, 104, 5));

    auto asks = book.get_asks();
    CHECK_EQ(asks.size(), static_cast<std::size_t>(3));
    CHECK_EQ(asks[0].price, static_cast<std::int64_t>(103));
    CHECK_EQ(asks[1].price, static_cast<std::int64_t>(104));
    CHECK_EQ(asks[2].price, static_cast<std::int64_t>(105));

    auto best = book.best_ask();
    CHECK(best.has_value());
    CHECK_EQ(best->price, static_cast<std::int64_t>(103));
    CHECK_EQ(best->order_id, static_cast<std::uint64_t>(2));
}

// 3. Time priority: at equal price, earlier arrival is ahead.
TEST_CASE(time_priority_fifo_at_same_price) {
    OrderBook book("BTC-USD");
    book.add_order(buy(10, 100, 5));  // arrives first
    book.add_order(buy(20, 100, 7));  // arrives second

    auto bids = book.get_bids();
    CHECK_EQ(bids.size(), static_cast<std::size_t>(2));
    CHECK_EQ(bids[0].order_id, static_cast<std::uint64_t>(10));
    CHECK_EQ(bids[1].order_id, static_cast<std::uint64_t>(20));
    // Sequence numbers reflect arrival order deterministically.
    CHECK(bids[0].sequence < bids[1].sequence);

    auto best = book.best_bid();
    CHECK(best.has_value());
    CHECK_EQ(best->order_id, static_cast<std::uint64_t>(10));
}

// 4. Cancel by id: order removed, best updates, unknown id returns false.
TEST_CASE(cancel_by_id_updates_best) {
    OrderBook book("BTC-USD");
    book.add_order(buy(1, 100, 5));
    book.add_order(buy(2, 102, 5));  // current best bid

    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(102));

    CHECK(book.cancel_order(2));  // remove the best
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK(book.best_bid().has_value());
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(100));

    // Cancelling something that isn't there.
    CHECK(!book.cancel_order(999));
    // Cancelling the same id twice: second time is false.
    CHECK(!book.cancel_order(2));
}

// 5. Cancel from the middle of a price level keeps the rest in order.
TEST_CASE(cancel_from_middle_of_level) {
    OrderBook book("BTC-USD");
    book.add_order(buy(1, 100, 5));
    book.add_order(buy(2, 100, 6));  // middle
    book.add_order(buy(3, 100, 7));

    CHECK(book.cancel_order(2));

    auto bids = book.get_bids();
    CHECK_EQ(bids.size(), static_cast<std::size_t>(2));
    CHECK_EQ(bids[0].order_id, static_cast<std::uint64_t>(1));
    CHECK_EQ(bids[1].order_id, static_cast<std::uint64_t>(3));
}

// 6. Empty book: best_* is a safe, clear "empty" signal (no crash).
TEST_CASE(empty_book_is_safe) {
    OrderBook book("BTC-USD");
    CHECK(book.empty());
    CHECK(!book.best_bid().has_value());
    CHECK(!book.best_ask().has_value());
    CHECK_EQ(book.get_bids().size(), static_cast<std::size_t>(0));
    CHECK_EQ(book.get_asks().size(), static_cast<std::size_t>(0));

    // Emptying a side by cancellation must reset best_* to empty.
    book.add_order(sell(1, 105, 5));
    CHECK(book.best_ask().has_value());
    CHECK(book.cancel_order(1));
    CHECK(!book.best_ask().has_value());
    CHECK(book.empty());
}

// 7. Mixed book: both sides ordered by price, then by arrival within a price.
TEST_CASE(mixed_book_full_priority_order) {
    OrderBook book("BTC-USD");
    // Bids
    book.add_order(buy(1, 100, 5));
    book.add_order(buy(2, 101, 5));
    book.add_order(buy(3, 101, 5));  // same price as id 2, arrives later
    // Asks
    book.add_order(sell(4, 105, 5));
    book.add_order(sell(5, 104, 5));
    book.add_order(sell(6, 104, 5));  // same price as id 5, arrives later

    // Best-of-book: no matching in 2A, so 101 bid and 104 ask coexist.
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(101));
    CHECK_EQ(book.best_bid()->order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(book.best_ask()->price, static_cast<std::int64_t>(104));
    CHECK_EQ(book.best_ask()->order_id, static_cast<std::uint64_t>(5));

    // Bids: 101(id2), 101(id3), 100(id1)
    auto bids = book.get_bids();
    CHECK_EQ(bids.size(), static_cast<std::size_t>(3));
    CHECK_EQ(bids[0].order_id, static_cast<std::uint64_t>(2));
    CHECK_EQ(bids[1].order_id, static_cast<std::uint64_t>(3));
    CHECK_EQ(bids[2].order_id, static_cast<std::uint64_t>(1));

    // Asks: 104(id5), 104(id6), 105(id4)
    auto asks = book.get_asks();
    CHECK_EQ(asks.size(), static_cast<std::size_t>(3));
    CHECK_EQ(asks[0].order_id, static_cast<std::uint64_t>(5));
    CHECK_EQ(asks[1].order_id, static_cast<std::uint64_t>(6));
    CHECK_EQ(asks[2].order_id, static_cast<std::uint64_t>(4));
}

// Duplicate order ids are rejected (keeps the cancel index consistent).
TEST_CASE(duplicate_order_id_rejected) {
    OrderBook book("BTC-USD");
    CHECK(book.add_order(buy(1, 100, 5)));
    CHECK(!book.add_order(buy(1, 999, 5)));  // same id rejected
    CHECK_EQ(book.size(), static_cast<std::size_t>(1));
    CHECK_EQ(book.best_bid()->price, static_cast<std::int64_t>(100));
}

int main() {
    return tf::run_all();
}
