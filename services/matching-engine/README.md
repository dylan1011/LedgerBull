# LedgerBull Matching Engine — Sub-phase 2A: Standalone Order Book

This is the **standalone C++ order book** data structure — the foundation of the
matching engine. It is intentionally isolated: **no matching logic, no networking,
no gRPC, no database, no Java integration.** Those arrive in later sub-phases (2B+).

## What it does

Maintains a single-symbol limit order book with **price-time priority** and supports:

- `add_order(Order)` — rest an order on the correct side.
- `cancel_order(order_id)` — remove an order by id (returns whether it existed).
- `best_bid()` / `best_ask()` — the best order on each side (or empty).
- `get_bids()` / `get_asks()` — full side snapshots in priority order.
- `to_string()` — dump both sides for inspection.

Crossing orders (a buy priced at/above the best ask) simply **rest** — no trade is
generated. Matching is sub-phase 2B.

## Design

| Concern | Choice | Why |
| --- | --- | --- |
| Price | `int64_t` **ticks** (`PRICE_SCALE = 100`) | No floating-point comparison bugs. |
| Bids | `std::map<Price, std::list<Order>, std::greater<>>` | Highest price first; ordered levels. |
| Asks | `std::map<Price, std::list<Order>, std::less<>>` | Lowest price first; ordered levels. |
| Level queue | `std::list<Order>` (FIFO) | Stable iterators → O(1) cancel from the middle. |
| Cancel index | `std::unordered_map<OrderId, {side, price, list-iterator}>` | O(1)-ish cancel, no book scan. |
| Time priority | monotonic `sequence` assigned on insert | Deterministic, wall-clock-independent, testable. |

## Build & run

```bash
cd services/matching-engine
cmake -S . -B build
cmake --build build

# tests
./build/order_book_tests      # or: ctest --test-dir build --output-on-failure

# demo
./build/order_book_demo
```

Requires a C++17 compiler and CMake ≥ 3.15.
