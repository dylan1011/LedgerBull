#pragma once

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

/// A single-symbol limit order book that maintains price-time priority.
///
/// Sub-phase 2A: data structure only. It rests orders and supports add / cancel /
/// query. It does NOT match crossing orders — a buy priced at or above the best ask
/// simply rests in the book. Matching arrives in sub-phase 2B.
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

private:
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
    BidLevels bids_;
    AskLevels asks_;
    std::unordered_map<OrderId, Locator> index_;

    template <typename Levels>
    static std::vector<Order> snapshot(const Levels& levels);
};

}  // namespace ledgerbull
