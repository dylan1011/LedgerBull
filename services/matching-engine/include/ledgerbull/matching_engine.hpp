#pragma once

#include "ledgerbull/event_log.hpp"
#include "ledgerbull/order_book.hpp"

#include <optional>
#include <string>
#include <unordered_map>
#include <vector>

namespace ledgerbull {

/// Write-ahead, event-sourced matching engine wrapper (sub-phase 2C).
///
/// Maintains one order book per trading symbol. Fills inherit the matched book's
/// (and order's) symbol — never a hardcoded default.
class MatchingEngine {
public:
    /// @param default_symbol trading symbol used by book() / empty QueryBook
    /// @param log_path       persistent append-only log file (see default_log_path())
    /// @param recover        if true and the log exists, replay it on construction
    MatchingEngine(std::string default_symbol, std::string log_path = EventLog::default_log_path(),
                   bool recover = true);

    std::vector<Fill> submit_order(const Order& order);
    bool cancel_order(OrderId order_id);

    /// Default symbol's book (tests / demos that use a single symbol).
    OrderBook& book();
    const OrderBook& book() const;

    /// Book for {@code symbol}, creating it if needed.
    OrderBook& book_for(const std::string& symbol);

    /// Existing book for {@code symbol}, or nullptr.
    const OrderBook* find_book(const std::string& symbol) const;

    /// Resting quantity for an order id across all symbol books.
    std::optional<Quantity> resting_quantity(OrderId order_id) const;

    const EventLog& log() const { return log_; }
    const std::vector<std::string>& replay_warnings() const { return replay_warnings_; }

private:
    std::string default_symbol_;
    std::unordered_map<std::string, OrderBook> books_;
    EventLog log_;
    std::vector<std::string> replay_warnings_;

    OrderBook& ensure_book(const std::string& symbol);
    void replay_from_log();
    void apply_event(const EngineEvent& event);
};

}  // namespace ledgerbull
