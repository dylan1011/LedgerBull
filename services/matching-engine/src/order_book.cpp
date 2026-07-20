#include "ledgerbull/order_book.hpp"

#include <algorithm>
#include <iterator>
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

bool OrderBook::add_order_with_sequence(const Order& incoming, Sequence seq) {
    if (index_.find(incoming.order_id) != index_.end()) {
        return false;
    }

    Order order = incoming;
    order.sequence = seq;
    next_sequence_ = std::max(next_sequence_, seq + 1);

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

void OrderBook::apply_maker_fill(OrderId maker_id, Quantity fill_qty) {
    auto idx = index_.find(maker_id);
    if (idx == index_.end()) {
        return;
    }

    Order& maker = *idx->second.it;
    maker.quantity -= fill_qty;
    if (maker.quantity <= 0) {
        cancel_order(maker_id);
    }
}

std::vector<Fill> OrderBook::submit_order_with_sequence(const Order& incoming, Sequence seq) {
    // Reject duplicate ids — the taker must not already be resting in the book.
    if (index_.find(incoming.order_id) != index_.end()) {
        return {};
    }

    // Reserve this sequence in the counter even if the order fully matches and never rests.
    next_sequence_ = std::max(next_sequence_, seq + 1);

    std::vector<Fill> fills;
    Quantity remaining = incoming.quantity;

    if (incoming.side == Side::BUY) {
        // Match against asks: best (lowest) price first, FIFO within level.
        while (remaining > 0) {
            const auto ask = best_ask();
            if (!ask) {
                break;
            }
            // LIMIT buy crosses when willing to pay >= best ask; MARKET always crosses.
            if (incoming.type == OrderType::LIMIT && incoming.price < ask->price) {
                break;
            }

            const Quantity match_qty = std::min(remaining, ask->quantity);
            fills.push_back(Fill{incoming.order_id, ask->order_id, ask->price, match_qty,
                                 incoming.symbol, next_trade_sequence_++});

            remaining -= match_qty;
            apply_maker_fill(ask->order_id, match_qty);
        }
    } else {
        // Match against bids: best (highest) price first, FIFO within level.
        while (remaining > 0) {
            const auto bid = best_bid();
            if (!bid) {
                break;
            }
            // LIMIT sell crosses when willing to accept <= best bid; MARKET always crosses.
            if (incoming.type == OrderType::LIMIT && incoming.price > bid->price) {
                break;
            }

            const Quantity match_qty = std::min(remaining, bid->quantity);
            fills.push_back(Fill{incoming.order_id, bid->order_id, bid->price, match_qty,
                                 incoming.symbol, next_trade_sequence_++});

            remaining -= match_qty;
            apply_maker_fill(bid->order_id, match_qty);
        }
    }

    // LIMIT remainder rests in the book with the pre-assigned sequence.
    if (remaining > 0 && incoming.type == OrderType::LIMIT) {
        Order rest = incoming;
        rest.quantity = remaining;
        add_order_with_sequence(rest, seq);
    }

    return fills;
}

std::vector<Fill> OrderBook::submit_order(const Order& incoming) {
    // Reject duplicate ids — the taker must not already be resting in the book.
    if (index_.find(incoming.order_id) != index_.end()) {
        return {};
    }

    std::vector<Fill> fills;
    Quantity remaining = incoming.quantity;

    if (incoming.side == Side::BUY) {
        // Match against asks: best (lowest) price first, FIFO within level.
        while (remaining > 0) {
            const auto ask = best_ask();
            if (!ask) {
                break;
            }
            // LIMIT buy crosses when willing to pay >= best ask; MARKET always crosses.
            if (incoming.type == OrderType::LIMIT && incoming.price < ask->price) {
                break;
            }

            const Quantity match_qty = std::min(remaining, ask->quantity);
            fills.push_back(Fill{incoming.order_id, ask->order_id, ask->price, match_qty,
                                 incoming.symbol, next_trade_sequence_++});

            remaining -= match_qty;
            apply_maker_fill(ask->order_id, match_qty);
        }
    } else {
        // Match against bids: best (highest) price first, FIFO within level.
        while (remaining > 0) {
            const auto bid = best_bid();
            if (!bid) {
                break;
            }
            // LIMIT sell crosses when willing to accept <= best bid; MARKET always crosses.
            if (incoming.type == OrderType::LIMIT && incoming.price > bid->price) {
                break;
            }

            const Quantity match_qty = std::min(remaining, bid->quantity);
            fills.push_back(Fill{incoming.order_id, bid->order_id, bid->price, match_qty,
                                 incoming.symbol, next_trade_sequence_++});

            remaining -= match_qty;
            apply_maker_fill(bid->order_id, match_qty);
        }
    }

    // LIMIT remainder rests in the book (reuses 2A add_order). MARKET remainder is
    // discarded — it never rests.
    if (remaining > 0 && incoming.type == OrderType::LIMIT) {
        Order rest = incoming;
        rest.quantity = remaining;
        add_order(rest);
    }

    return fills;
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
        std::copy(level.begin(), level.end(), std::back_inserter(out));
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
