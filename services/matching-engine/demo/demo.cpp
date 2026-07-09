#include "ledgerbull/order_book.hpp"

#include <iostream>

using ledgerbull::Fill;
using ledgerbull::Order;
using ledgerbull::OrderBook;
using ledgerbull::OrderType;
using ledgerbull::Side;

static void print_fills(const std::vector<Fill>& fills) {
    if (fills.empty()) {
        std::cout << "  (no fills)\n";
        return;
    }
    for (const Fill& f : fills) {
        std::cout << "  taker=" << f.taker_order_id << " maker=" << f.maker_order_id
                  << " price=" << f.price << " qty=" << f.quantity
                  << " trade_seq=" << f.sequence << "\n";
    }
}

int main() {
    OrderBook book("BTC-USD");

    std::cout << "=== LedgerBull matching demo (sub-phase 2B) ===\n";
    std::cout << "Trade price rule: resting (maker) order's price.\n";
    std::cout << "Market-order remainder policy: discard (never rests).\n\n";

    // Rest two ask levels.
    book.add_order(Order(1, "BTC-USD", Side::SELL, 105, 5));
    book.add_order(Order(2, "BTC-USD", Side::SELL, 106, 5));
    book.add_order(Order(3, "BTC-USD", Side::BUY, 100, 3));  // non-crossing bid

    std::cout << "Initial book:\n" << book.to_string() << "\n";

    // Incoming buy sweeps both ask levels (5 @105, then 3 @106).
    std::cout << "Submitting LIMIT BUY id=4 price=106 qty=8 (sweeps two ask levels)...\n";
    auto fills = book.submit_order(Order(4, "BTC-USD", Side::BUY, 106, 8, OrderType::LIMIT));

    std::cout << "Fills produced:\n";
    print_fills(fills);

    std::cout << "\nBook after sweep:\n" << book.to_string() << "\n";

    // After sweep, ask id=2 has qty 2 left at 106.
    std::cout << "Cancelling resting ask id=2 (qty 2 remaining at 106)...\n";
    book.cancel_order(2);
    std::cout << "\nBook after cancel:\n" << book.to_string();

    return 0;
}
