#pragma once

#include "ledgerbull/event_log.hpp"
#include "ledgerbull/order_book.hpp"

#include <string>
#include <vector>

namespace ledgerbull {

/// Write-ahead, event-sourced matching engine wrapper (sub-phase 2C).
///
/// Public API mirrors the book (`submit_order`, `cancel_order`, queries) but every
/// state change is appended to the local event log **before** being applied. On startup
/// the log is replayed through the same matching path so the in-memory book is rebuilt.
class MatchingEngine {
public:
    /// @param symbol         trading symbol for the book
    /// @param log_path       persistent append-only log file (see default_log_path())
    /// @param recover        if true and the log exists, replay it on construction
    MatchingEngine(std::string symbol, std::string log_path = EventLog::default_log_path(),
                   bool recover = true);

    std::vector<Fill> submit_order(const Order& order);
    bool cancel_order(OrderId order_id);

    OrderBook& book() { return book_; }
    const OrderBook& book() const { return book_; }

    const EventLog& log() const { return log_; }
    const std::vector<std::string>& replay_warnings() const { return replay_warnings_; }

private:
    OrderBook book_;
    EventLog log_;
    std::vector<std::string> replay_warnings_;

    void replay_from_log();
    void apply_event(const EngineEvent& event);
};

}  // namespace ledgerbull
