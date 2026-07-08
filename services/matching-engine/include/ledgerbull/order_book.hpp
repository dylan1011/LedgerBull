#pragma once

#include "ledgerbull/fill.hpp"
#include "ledgerbull/order.hpp"

#include <cstddef>
#include <functional>
#include <list>
#include <map>
#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace ledgerbull {

/// A single-symbol order book with price-time priority and limit/market matching.
///
/// Sub-phase 2A: resting book (add / cancel / query).
/// Sub-phase 2B: `submit_order` matches crossing orders under price-time priority.
/// Trades execute at the **maker** (resting order's) price. LIMIT remainders rest;
/// MARKET remainders are discarded.
///
/// Ordering rules:
///   - Bids: highest price first (a buyer paying more is ahead).
///   - Asks: lowest price first  (a seller accepting less is ahead).
///   - Within a price level: earliest arrival (lowest sequence) first (FIFO).
class OrderBook {
public:
    explicit OrderBook(std::string symbol);

    /// Rest an order in the book. The book assigns its arrival sequence.
    /// Returns false (and inserts nothing) if `order_id` already exists.
    bool add_order(const Order& order);

    /// Submit an aggressing order and match it against the opposite side under
    /// price-time priority (sub-phase 2B). Produces zero or more fills, executed at
    /// each resting (maker) order's price. A LIMIT order's unfilled remainder rests
    /// in the book; a MARKET order's remainder is discarded. Returns the fills in
    /// execution order.
    std::vector<Fill> submit_order(const Order& order);

    /// Remove an order by id. Returns true if it was found and removed.
    bool cancel_order(OrderId order_id);

    /// Best (front-of-queue) bid / ask, or std::nullopt if that side is empty.
    std::optional<Order> best_bid() const;
    std::optional<Order> best_ask() const;

    /// Full side snapshots in strict priority order (best first).
    std::vector<Order> get_bids() const;
    std::vector<Order> get_asks() const;

    bool empty() const;
    std::size_t size() const;
    const std::string& symbol() const { return symbol_; }

    /// Multi-line dump of both sides in priority order, for inspection/verification.
    std::string to_string() const;

    /// Next sequence number that would be assigned (for write-ahead logging reservation).
    Sequence peek_next_sequence() const { return next_sequence_; }

    /// Submit with a pre-assigned sequence (used by the event log on live apply and replay).
    /// Advances `next_sequence_` to at least `seq + 1` even if the order never rests.
    std::vector<Fill> submit_order_with_sequence(const Order& order, Sequence seq);

private:
    /// Insert a resting order with an exact sequence (replay / write-ahead path).
    bool add_order_with_sequence(const Order& order, Sequence seq);
    // A price level holds its orders in a std::list so cancels from the middle are
    // O(1) and the stored iterators stay valid when other orders are erased.
    using Level = std::list<Order>;

    // Bids sorted highest-first; asks sorted lowest-first. std::map keeps price levels
    // ordered and its node references/iterators stable across inserts and erases.
    using BidLevels = std::map<Price, Level, std::greater<Price>>;
    using AskLevels = std::map<Price, Level, std::less<Price>>;

    // id -> exact location, so cancel never scans the book.
    struct Locator {
        Side side;
        Price price;
        Level::iterator it;
    };

    std::string symbol_;
    Sequence next_sequence_{1};
    Sequence next_trade_sequence_{1};
    BidLevels bids_;
    AskLevels asks_;
    std::unordered_map<OrderId, Locator> index_;

    /// Reduce a resting maker's quantity after a fill; remove it if fully consumed.
    void apply_maker_fill(OrderId maker_id, Quantity fill_qty);

    template <typename Levels>
    static std::vector<Order> snapshot(const Levels& levels);
};

}  // namespace ledgerbull
