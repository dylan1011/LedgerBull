#pragma once

#include <cstdint>
#include <string>
#include <utility>

namespace ledgerbull {

/// Which side of the book an order rests on.
enum class Side { BUY, SELL };

/// Human-readable side name.
inline const char* to_string(Side side) {
    return side == Side::BUY ? "BUY" : "SELL";
}

// --- Numeric types -----------------------------------------------------------
//
// Price is stored as an integer number of "ticks" (the smallest price increment),
// NEVER as a floating-point value. This eliminates floating-point equality and
// comparison bugs that would otherwise corrupt price ordering. The scale below
// documents how a human price maps to ticks; callers convert once at the edge.
//
//   PRICE_SCALE = 100  =>  1 tick = 0.01 quote units
//                          human price 101.23  ->  10123 ticks
//
// This sub-phase's tests use small whole tick values (e.g. 100, 101, 102) directly.

/// Ticks per one unit of quote currency (documented price scale).
inline constexpr std::int64_t PRICE_SCALE = 100;

using Price = std::int64_t;       ///< price in integer ticks
using Quantity = std::int64_t;    ///< remaining quantity (integer units)
using OrderId = std::uint64_t;    ///< unique order identifier
using Sequence = std::uint64_t;   ///< monotonic arrival number (time priority)

/// A single resting order.
///
/// `sequence` is assigned by the OrderBook on insertion (monotonic counter), giving
/// deterministic, wall-clock-independent time priority that is easy to test.
struct Order {
    OrderId order_id{0};
    std::string symbol;
    Side side{Side::BUY};
    Price price{0};        // in ticks
    Quantity quantity{0};  // remaining quantity
    Sequence sequence{0};  // arrival order, assigned by the book

    Order() = default;

    Order(OrderId id, std::string sym, Side s, Price p, Quantity q)
        : order_id(id), symbol(std::move(sym)), side(s), price(p), quantity(q) {}
};

}  // namespace ledgerbull
