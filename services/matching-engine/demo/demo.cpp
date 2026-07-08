#include "ledgerbull/order_book.hpp"

#include <iostream>

using ledgerbull::Order;
using ledgerbull::OrderBook;
using ledgerbull::Side;

int main() {
    OrderBook book("BTC-USD");

    std::cout << "=== LedgerBull order book demo (sub-phase 2A: no matching) ===\n\n";

    // Add a handful of orders. Prices are in integer ticks.
    book.add_order(Order(1, "BTC-USD", Side::BUY, 100, 5));
    book.add_order(Order(2, "BTC-USD", Side::BUY, 101, 3));
    book.add_order(Order(3, "BTC-USD", Side::BUY, 101, 8));   // same price as id 2, later
    book.add_order(Order(4, "BTC-USD", Side::SELL, 104, 4));
    book.add_order(Order(5, "BTC-USD", Side::SELL, 104, 2));  // same price as id 4, later
    book.add_order(Order(6, "BTC-USD", Side::SELL, 106, 9));

    std::cout << "After adding 6 orders:\n";
    std::cout << book.to_string() << "\n";

    auto bid = book.best_bid();
    auto ask = book.best_ask();
    std::cout << "best bid: price=" << (bid ? bid->price : 0)
              << " id=" << (bid ? bid->order_id : 0) << "\n";
    std::cout << "best ask: price=" << (ask ? ask->price : 0)
              << " id=" << (ask ? ask->order_id : 0) << "\n\n";

    std::cout << "Cancelling order id=2 (a 101 bid, front of that level)...\n\n";
    bool removed = book.cancel_order(2);
    std::cout << "cancel_order(2) -> " << (removed ? "true" : "false") << "\n\n";

    std::cout << "After cancelling id=2:\n";
    std::cout << book.to_string() << "\n";

    auto bid2 = book.best_bid();
    std::cout << "best bid is now: price=" << (bid2 ? bid2->price : 0)
              << " id=" << (bid2 ? bid2->order_id : 0)
              << "  (id=3 now leads the 101 level)\n";

    return 0;
}
