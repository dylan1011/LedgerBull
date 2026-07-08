#include "ledgerbull/order_book.hpp"

#include <sstream>
#include <utility>

namespace ledgerbull {

OrderBook::OrderBook(std::string symbol) : symbol_(std::move(symbol)) {}

bool OrderBook::add_order(const Order& incoming) {
    // Reject duplicate ids so the id -> location index stays consistent.
    if (index_.find(incoming.order_id) != index_.end()) {
        return false;
    }

    Order order = incoming;
    order.sequence = next_sequence_++;  // deterministic arrival order (time priority)

    if (order.side == Side::BUY) {
        Level& level = bids_[order.price];
        level.push_back(order);
        index_[order.order_id] = Locator{Side::BUY, order.price, std::prev(level.end())};
    } else {
        Level& level = asks_[order.price];
        level.push_back(order);
        index_[order.order_id] = Locator{Side::SELL, order.price, std::prev(level.end())};
    }
    return true;
}

bool OrderBook::cancel_order(OrderId order_id) {
    auto idx = index_.find(order_id);
    if (idx == index_.end()) {
        return false;
    }

    const Locator& loc = idx->second;
    if (loc.side == Side::BUY) {
        auto level = bids_.find(loc.price);
        if (level != bids_.end()) {
            level->second.erase(loc.it);
            if (level->second.empty()) {
                bids_.erase(level);  // drop empty price levels so best_* stays correct
            }
        }
    } else {
        auto level = asks_.find(loc.price);
        if (level != asks_.end()) {
            level->second.erase(loc.it);
            if (level->second.empty()) {
                asks_.erase(level);
            }
        }
    }

    index_.erase(idx);
    return true;
}

std::optional<Order> OrderBook::best_bid() const {
    if (bids_.empty()) {
        return std::nullopt;
    }
    // First map entry is the highest bid price; front of its list is the earliest arrival.
    return bids_.begin()->second.front();
}

std::optional<Order> OrderBook::best_ask() const {
    if (asks_.empty()) {
        return std::nullopt;
    }
    // First map entry is the lowest ask price; front of its list is the earliest arrival.
    return asks_.begin()->second.front();
}

template <typename Levels>
std::vector<Order> OrderBook::snapshot(const Levels& levels) {
    std::vector<Order> out;
    for (const auto& [price, level] : levels) {
        (void)price;
        for (const auto& order : level) {
            out.push_back(order);
        }
    }
    return out;
}

std::vector<Order> OrderBook::get_bids() const {
    return snapshot(bids_);
}

std::vector<Order> OrderBook::get_asks() const {
    return snapshot(asks_);
}

bool OrderBook::empty() const {
    return index_.empty();
}

std::size_t OrderBook::size() const {
    return index_.size();
}

std::string OrderBook::to_string() const {
    std::ostringstream os;
    os << "OrderBook[" << symbol_ << "]\n";

    os << "  ASKS (best = lowest price first):\n";
    if (asks_.empty()) {
        os << "    <empty>\n";
    } else {
        for (const Order& o : get_asks()) {
            os << "    price=" << o.price << " qty=" << o.quantity
               << " id=" << o.order_id << " seq=" << o.sequence << "\n";
        }
    }

    os << "  BIDS (best = highest price first):\n";
    if (bids_.empty()) {
        os << "    <empty>\n";
    } else {
        for (const Order& o : get_bids()) {
            os << "    price=" << o.price << " qty=" << o.quantity
               << " id=" << o.order_id << " seq=" << o.sequence << "\n";
        }
    }
    return os.str();
}

}  // namespace ledgerbull
