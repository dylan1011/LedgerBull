# LedgerBull Matching Engine — C++ (Sub-phases 2A–2D)

Standalone C++ matching engine foundation. **Java integration is sub-phase 2E** — 2D adds
a C++ gRPC server tested with grpcurl only.

## Sub-phase 2A: Order book data structure

Maintains a single-symbol book with **price-time priority** (`add_order`, `cancel_order`,
`best_bid`/`best_ask`, `get_bids`/`get_asks`, `to_string`).

## Sub-phase 2B: Matching logic

`submit_order(Order) -> std::vector<Fill>` matches crossing orders under price-time
priority. Trades execute at the **maker** (resting) price. LIMIT remainders rest;
MARKET remainders are discarded.

## Sub-phase 2C: Crash-recovery event log

`MatchingEngine` wraps the book with **write-ahead, append-only event sourcing**:

1. **Append event to disk and `flush()`** (before mutating the book).
2. **Apply** to the in-memory book (`submit_order_with_sequence` / `cancel_order`).

On startup the log is **replayed in order** through the same matching path so the book
is rebuilt identically after a crash.

### Event format (pipe-delimited, one record per line)

```
idx|SUBMIT|order_id|symbol|side|order_type|price|quantity|sequence|checksum
idx|CANCEL|order_id|checksum
```

- **sequence** is persisted on SUBMIT and restored exactly on replay (time priority).
- **checksum** per line detects corruption (trailing partial writes are skipped).

### Durability

Each append calls `flush()` before the book is updated — a practical write-ahead middle
ground between speed and crash safety. Full `fsync()` per event would be safer against
power loss but slower; can be added later if needed.

### Log path (hosting-safe)

Default: `./data/engine-events.log` (persistent relative path, **not** `/tmp`).

Override with environment variable:

```bash
export LEDGERBULL_ENGINE_LOG_PATH=/var/lib/ledgerbull/engine-events.log
```

**Deployment notes:**

- On a VM, place the log on **persistent disk** (not ephemeral storage).
- If containerized, mount the log directory as a **Docker volume** so it survives
  container recreation. Example:

```yaml
services:
  matching-engine:
    volumes:
      - ledgerbull_engine_data:/var/lib/ledgerbull
    environment:
      LEDGERBULL_ENGINE_LOG_PATH: /var/lib/ledgerbull/engine-events.log

volumes:
  ledgerbull_engine_data:
```

Total-VM-loss protection (backups) is Phase 12.

## Sub-phase 2D: gRPC server (C++ only)

`matching_engine_server` exposes the engine over gRPC. Handlers are a **thin adapter** —
they translate protobuf ↔ engine types and delegate to `MatchingEngine` (matching,
ordering, and event logging unchanged).

### Prerequisites (macOS / Homebrew)

```bash
brew install grpc protobuf grpcurl
cmake -S . -B build -DCMAKE_PREFIX_PATH="$(brew --prefix grpc);$(brew --prefix protobuf)"
cmake --build build
```

### Configuration

| Setting | Default | Env var |
| --- | --- | --- |
| gRPC port | `50051` | `LEDGERBULL_ENGINE_GRPC_PORT` |
| Event log | `./data/engine-events.log` | `LEDGERBULL_ENGINE_LOG_PATH` |

Server reflection is enabled so **grpcurl** can discover services without a local `.proto`.

### Proto contract

Source: `proto/matching_engine.proto` — package **`ledgerbull.api`** (separate from the
C++ `ledgerbull::` engine namespace to avoid name collisions). Service:
`ledgerbull.api.MatchingEngine` with `SubmitOrder`, `CancelOrder`, `QueryBook`.

Prices and quantities are **`int64` ticks** on the wire, matching the engine.

### Run & test

```bash
# start server
LEDGERBULL_ENGINE_LOG_PATH=./data/engine-events.log ./build/matching_engine_server

# grpcurl smoke test (submit → match/fill → query → cancel → crash recovery)
bash scripts/grpc_smoke_test.sh
```

Example:

```bash
grpcurl -plaintext -d '{"order_id":"2","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":105,"quantity":3}' \
  localhost:50051 ledgerbull.api.MatchingEngine/SubmitOrder
```

## Build & run

```bash
cd services/matching-engine
cmake -S . -B build
cmake --build build

# all tests (2A + 2B + 2C) — 28 tests
ctest --test-dir build --output-on-failure

# gRPC server + grpcurl smoke test (2D)
./build/matching_engine_server
bash scripts/grpc_smoke_test.sh

# matching demo (2B)
./build/order_book_demo

# crash-recovery demo (2C)
./build/crash_recovery_demo
```

Requires C++17, CMake ≥ 3.15, and Homebrew `grpc` + `protobuf` for the gRPC server target.
