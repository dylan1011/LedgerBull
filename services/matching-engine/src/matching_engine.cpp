#include "ledgerbull/matching_engine.hpp"

#include <algorithm>
#include <stdexcept>

namespace ledgerbull {

MatchingEngine::MatchingEngine(std::string default_symbol, std::string log_path, bool recover)
    : default_symbol_(std::move(default_symbol)), log_(std::move(log_path)) {
    ensure_book(default_symbol_);
    if (recover) {
        replay_from_log();
    }
}

OrderBook& MatchingEngine::ensure_book(const std::string& symbol) {
    auto it = books_.find(symbol);
    if (it == books_.end()) {
        it = books_.emplace(symbol, OrderBook(symbol)).first;
    }
    return it->second;
}

OrderBook& MatchingEngine::book() {
    return ensure_book(default_symbol_);
}

const OrderBook& MatchingEngine::book() const {
    auto it = books_.find(default_symbol_);
    if (it == books_.end()) {
        throw std::logic_error("default symbol book missing: " + default_symbol_);
    }
    return it->second;
}

OrderBook& MatchingEngine::book_for(const std::string& symbol) {
    return ensure_book(symbol);
}

const OrderBook* MatchingEngine::find_book(const std::string& symbol) const {
    auto it = books_.find(symbol);
    if (it == books_.end()) {
        return nullptr;
    }
    return &it->second;
}

std::optional<Quantity> MatchingEngine::resting_quantity(OrderId order_id) const {
    for (const auto& [_, book] : books_) {
        const auto bids = book.get_bids();
        auto bid_it = std::find_if(bids.begin(), bids.end(),
                                   [&](const auto& o) { return o.order_id == order_id; });
        if (bid_it != bids.end()) {
            const auto& o = *bid_it;
            return o.quantity;
        }
        const auto asks = book.get_asks();
        auto ask_it = std::find_if(asks.begin(), asks.end(),
                                   [&](const auto& o) { return o.order_id == order_id; });
        if (ask_it != asks.end()) {
            const auto& o = *ask_it;
            return o.quantity;
        }
    }
    return std::nullopt;
}

void MatchingEngine::replay_from_log() {
    const auto events = log_.load_all(&replay_warnings_);
    for (const EngineEvent& event : events) {
        apply_event(event);
    }
}

void MatchingEngine::apply_event(const EngineEvent& event) {
    if (event.type == EventType::SUBMIT) {
        Order order(event.order_id, event.symbol, event.side, event.price, event.quantity,
                    event.order_type);
        ensure_book(event.symbol).submit_order_with_sequence(order, event.sequence);
        return;
    }
    for (auto& [_, book] : books_) {
        if (book.cancel_order(event.order_id)) {
            return;
        }
    }
}

std::vector<Fill> MatchingEngine::submit_order(const Order& order) {
    OrderBook& book = ensure_book(order.symbol);
    const Sequence seq = book.peek_next_sequence();
    log_.append_submit(order, seq);
    return book.submit_order_with_sequence(order, seq);
}

bool MatchingEngine::cancel_order(OrderId order_id) {
    log_.append_cancel(order_id);
    for (auto& [_, book] : books_) {
        if (book.cancel_order(order_id)) {
            return true;
        }
    }
    return false;
}

}  // namespace ledgerbull
