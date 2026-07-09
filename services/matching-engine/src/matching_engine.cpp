#include "ledgerbull/matching_engine.hpp"

namespace ledgerbull {

MatchingEngine::MatchingEngine(std::string symbol, std::string log_path, bool recover)
    : book_(std::move(symbol)), log_(std::move(log_path)) {
    if (recover) {
        replay_from_log();
    }
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
        book_.submit_order_with_sequence(order, event.sequence);
        return;
    }
    book_.cancel_order(event.order_id);
}

std::vector<Fill> MatchingEngine::submit_order(const Order& order) {
    const Sequence seq = book_.peek_next_sequence();
    log_.append_submit(order, seq);
    return book_.submit_order_with_sequence(order, seq);
}

bool MatchingEngine::cancel_order(OrderId order_id) {
    log_.append_cancel(order_id);
    return book_.cancel_order(order_id);
}

}  // namespace ledgerbull
