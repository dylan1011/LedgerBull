#include "ledgerbull/matching_engine.hpp"
#include "test_framework.hpp"

#include <cstdint>
#include <filesystem>
#include <fstream>
#include <string>

using ledgerbull::EventLog;
using ledgerbull::MatchingEngine;
using ledgerbull::Order;
using ledgerbull::OrderBook;
using ledgerbull::OrderType;
using ledgerbull::Side;

namespace {

std::string test_log_path(const char* name) {
    return std::string("data/test/") + name + ".log";
}

void remove_log(const std::string& path) {
    std::error_code ec;
    std::filesystem::remove(path, ec);
}

Order limit_buy(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::BUY, price, qty, OrderType::LIMIT);
}

Order limit_sell(std::uint64_t id, std::int64_t price, std::int64_t qty) {
    return Order(id, "BTC-USD", Side::SELL, price, qty, OrderType::LIMIT);
}

std::size_t count_lines(const std::string& path) {
    std::ifstream in(path);
    std::size_t n = 0;
    std::string line;
    while (std::getline(in, line)) {
        if (!line.empty()) {
            ++n;
        }
    }
    return n;
}

bool order_vectors_equal(const std::vector<Order>& a, const std::vector<Order>& b) {
    if (a.size() != b.size()) {
        return false;
    }
    for (std::size_t i = 0; i < a.size(); ++i) {
        if (a[i].order_id != b[i].order_id || a[i].price != b[i].price ||
            a[i].quantity != b[i].quantity || a[i].sequence != b[i].sequence ||
            a[i].side != b[i].side) {
            return false;
        }
    }
    return true;
}

}  // namespace

// 1. Log-then-apply: events appear on disk in order.
TEST_CASE(log_then_apply_writes_events_in_order) {
    const std::string path = test_log_path("log_then_apply");
    remove_log(path);

    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_sell(1, 105, 5));
        engine.submit_order(limit_buy(2, 100, 3));
        engine.cancel_order(1);
    }

    CHECK_EQ(count_lines(path), static_cast<std::size_t>(3));

    const auto events = EventLog(path).load_all();
    CHECK_EQ(events.size(), static_cast<std::size_t>(3));
    CHECK(events[0].type == ledgerbull::EventType::SUBMIT);
    CHECK_EQ(events[0].order_id, static_cast<std::uint64_t>(1));
    CHECK_EQ(events[0].sequence, static_cast<std::uint64_t>(1));
    CHECK(events[1].type == ledgerbull::EventType::SUBMIT);
    CHECK_EQ(events[1].order_id, static_cast<std::uint64_t>(2));
    CHECK(events[2].type == ledgerbull::EventType::CANCEL);
    CHECK_EQ(events[2].order_id, static_cast<std::uint64_t>(1));
}

// 2. Rebuild equivalence after a rich sequence of operations.
TEST_CASE(rebuild_equivalence_after_crash) {
    const std::string path = test_log_path("rebuild_equivalence");
    remove_log(path);

    std::vector<ledgerbull::Order> bids_before;
    std::vector<ledgerbull::Order> asks_before;
    std::size_t size_before = 0;
    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_sell(1, 105, 10));
        engine.submit_order(limit_sell(2, 106, 5));
        engine.submit_order(limit_buy(3, 106, 15));  // sweeps both asks completely
        engine.submit_order(limit_buy(4, 100, 2));   // rests
        engine.submit_order(limit_buy(5, 99, 1));    // rests, then cancelled
        engine.cancel_order(5);
        bids_before = engine.book().get_bids();
        asks_before = engine.book().get_asks();
        size_before = engine.book().size();
    }

    MatchingEngine rebuilt("BTC-USD", path, true);
    CHECK_EQ(rebuilt.book().size(), size_before);
    CHECK(order_vectors_equal(rebuilt.book().get_bids(), bids_before));
    CHECK(order_vectors_equal(rebuilt.book().get_asks(), asks_before));
    CHECK_EQ(rebuilt.book().size(), static_cast<std::size_t>(1));
    CHECK(rebuilt.book().best_bid().has_value());
    CHECK_EQ(rebuilt.book().best_bid()->order_id, static_cast<std::uint64_t>(4));
}

// 3. Matches reproduce: crossing orders leave identical resting state.
TEST_CASE(matches_reproduce_on_replay) {
    const std::string path = test_log_path("matches_reproduce");
    remove_log(path);

    std::vector<ledgerbull::Order> bids_before;
    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_sell(1, 105, 4));
        engine.submit_order(limit_buy(2, 105, 10));  // partial fill + resting bid
        bids_before = engine.book().get_bids();
    }

    MatchingEngine rebuilt("BTC-USD", path, true);
    CHECK(order_vectors_equal(rebuilt.book().get_bids(), bids_before));
    CHECK(!rebuilt.book().best_ask().has_value());
    CHECK(rebuilt.book().best_bid().has_value());
    CHECK_EQ(rebuilt.book().best_bid()->quantity, static_cast<std::int64_t>(6));
}

// 4. Cancel reproduces: cancelled order absent after replay.
TEST_CASE(cancel_reproduces_on_replay) {
    const std::string path = test_log_path("cancel_reproduces");
    remove_log(path);

    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_buy(1, 100, 5));
        engine.cancel_order(1);
    }

    MatchingEngine rebuilt("BTC-USD", path, true);
    CHECK(rebuilt.book().empty());
    CHECK(!rebuilt.book().best_bid().has_value());
}

// 5. Sequence preserved: FIFO order identical after replay.
TEST_CASE(sequence_preserved_on_replay) {
    const std::string path = test_log_path("sequence_preserved");
    remove_log(path);

    std::vector<ledgerbull::Order> bids_before;
    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_buy(1, 100, 5));
        engine.submit_order(limit_buy(2, 100, 7));
        bids_before = engine.book().get_bids();
    }

    MatchingEngine rebuilt("BTC-USD", path, true);
    const auto bids_after = rebuilt.book().get_bids();
    CHECK_EQ(bids_after.size(), bids_before.size());
    CHECK_EQ(bids_after[0].order_id, bids_before[0].order_id);
    CHECK_EQ(bids_after[0].sequence, bids_before[0].sequence);
    CHECK_EQ(bids_after[1].order_id, bids_before[1].order_id);
    CHECK_EQ(bids_after[1].sequence, bids_before[1].sequence);
    CHECK(bids_after[0].sequence < bids_after[1].sequence);
}

// 6. Missing log -> empty book, no crash.
TEST_CASE(missing_log_starts_empty) {
    const std::string path = test_log_path("missing_log");
    remove_log(path);

    MatchingEngine engine("BTC-USD", path, true);
    CHECK(engine.book().empty());
    CHECK_EQ(engine.replay_warnings().size(), static_cast<std::size_t>(0));
}

// 7. Corrupt trailing line skipped gracefully.
TEST_CASE(corrupt_trailing_line_skipped) {
    const std::string path = test_log_path("corrupt_trailing");
    remove_log(path);

    {
        MatchingEngine engine("BTC-USD", path, false);
        engine.submit_order(limit_buy(1, 100, 5));
    }

    {
        std::ofstream out(path, std::ios::app);
        out << "9|SUBMIT|broken_line_without_enough_fields\n";
    }

    MatchingEngine rebuilt("BTC-USD", path, true);
    CHECK_EQ(rebuilt.book().size(), static_cast<std::size_t>(1));
    CHECK(rebuilt.book().best_bid().has_value());
    CHECK_EQ(rebuilt.replay_warnings().size(), static_cast<std::size_t>(1));
}
