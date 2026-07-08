# LedgerBull Matching Engine — C++ (Sub-phases 2A + 2B)

Standalone C++ matching engine foundation. **No networking, gRPC, persistence, or Java**
in this stage — those arrive in sub-phases 2C–2E.

## Sub-phase 2A: Order book data structure

Maintains a single-symbol book with **price-time priority**:

- `add_order(Order)` — rest an order on the correct side.
- `cancel_order(order_id)` — remove by id.
- `best_bid()` / `best_ask()` — best order on each side (or empty).
- `get_bids()` / `get_asks()` — full side snapshots in priority order.
- `to_string()` — dump both sides for inspection.

## Sub-phase 2B: Matching logic

`submit_order(Order) -> std::vector<Fill>` matches crossing orders under price-time priority:

- **Crossing rules:** BUY crosses when price ≥ best ask; SELL when price ≤ best bid.
  MARKET orders have no price limit and match until filled or the opposite side is empty.
- **Trade price:** each fill executes at the **maker** (resting order's) price — standard
  price-improvement rule. A buy at 105 matching a resting ask at 103 fills at 103.
- **Partial fills:** `match_qty = min(taker.remaining, maker.remaining)`; fully consumed
  makers are removed; partially filled makers stay in the book with reduced quantity.
- **LIMIT remainder:** rests in the book via `add_order` (reuses 2A logic).
- **MARKET remainder:** **discarded** — never rests in the book.

## Design

| Concern | Choice | Why |
| --- | --- | --- |
| Price | `int64_t` **ticks** (`PRICE_SCALE = 100`) | No floating-point comparison bugs. |
| Bids | `std::map<Price, std::list<Order>, std::greater<>>` | Highest price first; ordered levels. |
| Asks | `std::map<Price, std::list<Order>, std::less<>>` | Lowest price first; ordered levels. |
| Level queue | `std::list<Order>` (FIFO) | Stable iterators → O(1) cancel from the middle. |
| Cancel index | `std::unordered_map<OrderId, {side, price, list-iterator}>` | O(1)-ish cancel, no book scan. |
| Time priority | monotonic `sequence` assigned on insert | Deterministic, wall-clock-independent, testable. |
| Quantity conservation | `match_qty = min(taker, maker)` per fill | No quantity created or lost; asserted in tests. |

## Build & run

```bash
cd services/matching-engine
cmake -S . -B build
cmake --build build

# tests (2A + 2B)
./build/order_book_tests      # or: ctest --test-dir build --output-on-failure

# demo (multi-level sweep + fills)
./build/order_book_demo
```

Requires a C++17 compiler and CMake ≥ 3.15.
