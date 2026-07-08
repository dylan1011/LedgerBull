#include "ledgerbull/matching_engine.hpp"

#include <iostream>
#include <string>

using ledgerbull::MatchingEngine;
using ledgerbull::Order;
using ledgerbull::OrderType;
using ledgerbull::Side;
using ledgerbull::books_equal;

int main() {
    const std::string log_path = "./data/demo-crash-recovery.log";

    std::cout << "=== LedgerBull crash-recovery demo (sub-phase 2C) ===\n";
    std::cout << "Log path: " << log_path << " (persistent, not /tmp)\n";
    std::cout << "Durability: flush() per event before book apply (write-ahead).\n\n";

    std::string before_dump;
    {
        MatchingEngine engine("BTC-USD", log_path, false);
        engine.submit_order(Order(1, "BTC-USD", Side::SELL, 105, 5));
        engine.submit_order(Order(2, "BTC-USD", Side::SELL, 106, 5));
        engine.submit_order(Order(3, "BTC-USD", Side::BUY, 106, 8, OrderType::LIMIT));  // sweep
        engine.submit_order(Order(4, "BTC-USD", Side::BUY, 100, 3));
        engine.cancel_order(2);

        before_dump = engine.book().to_string();
        std::cout << "Book BEFORE simulated crash:\n" << before_dump << "\n";
    }
    // engine destroyed here — simulates crash; log remains on disk.

    std::cout << "Simulated crash (engine destroyed). Reopening same log...\n\n";

    MatchingEngine recovered("BTC-USD", log_path, true);
    const std::string after_dump = recovered.book().to_string();
    std::cout << "Book AFTER replay:\n" << after_dump << "\n";

    if (before_dump == after_dump) {
        std::cout << "RESULT: books are IDENTICAL — crash recovery succeeded.\n";
    } else {
        std::cout << "RESULT: MISMATCH — crash recovery failed.\n";
        return 1;
    }

    if (!recovered.replay_warnings().empty()) {
        std::cout << "Replay warnings:\n";
        for (const auto& w : recovered.replay_warnings()) {
            std::cout << "  - " << w << '\n';
        }
    }

    return 0;
}
