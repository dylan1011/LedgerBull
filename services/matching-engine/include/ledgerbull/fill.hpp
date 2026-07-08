#pragma once

#include "ledgerbull/order.hpp"

#include <string>

namespace ledgerbull {

/// A trade produced when an incoming (taker) order matches a resting (maker) order.
///
/// The trade executes at the **maker's** (resting order's) price — the standard
/// price-improvement rule: the aggressor never gets a worse price than its limit, and
/// any price improvement accrues to it.
struct Fill {
    OrderId taker_order_id{0};  // the incoming / aggressing order
    OrderId maker_order_id{0};  // the resting order that was matched
    Price price{0};             // trade price in ticks == maker/resting order's price
    Quantity quantity{0};       // matched quantity
    std::string symbol;
    Sequence sequence{0};       // monotonic trade sequence (execution order)
};

}  // namespace ledgerbull
