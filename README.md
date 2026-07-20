# LedgerBull

**LedgerBull** is a real-time trading & risk platform: it ingests live market data, validates and matches orders through a low-latency engine, tracks their full execution lifecycle, computes positions and PnL, and (in later phases) monitors risk — built as parallel microservices across C++, Java, and (later) Python.

> **Scope note:** This is a demonstration platform built on free-tier infrastructure. It uses real market data and a genuine matching engine, but does **not** execute real trades (that requires broker-dealer licensing). It demonstrates production-grade engineering practices and the architecture of a real trading system.

---

## Status

**Completed and verified through Phase 4** — the full trading core plus positions and PnL works end to end: live market data in → order validated → matched by the C++ engine → order & fills persisted → order state tracked (both sides of every trade) → queryable and cancellable → positions, realized PnL (FIFO), and unrealized PnL (live price) computed and queryable.

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Project scaffolding & tooling | ✅ Complete |
| 1 | Spring Cloud foundation + live market data ingestion | ✅ Complete |
| 2 | Matching engine (order book, matching, crash recovery, gRPC) + execution | ✅ Complete |
| 3 | Execution lifecycle — order state machine, persistence, query & cancel | ✅ Complete |
| 4 | Position & PnL tracking (net position, FIFO realized PnL, live unrealized PnL, query endpoints) | ✅ Complete |
| 5 | Risk engine | ⬜ Planned |
| 6 | AI/ML signals (Python sidecar) | ⬜ Planned |
| 7 | Integration (full data → decision → execution → risk pipeline) | ⬜ Planned |
| 8 | Security hardening (TLS/mTLS, auth, rate limiting) | ⬜ Planned |
| 9 | Reliability (circuit breakers, failover, graceful degradation) | ⬜ Planned |
| 10 | Observability (metrics, tracing, logs, alerting) | ⬜ Planned |
| 11 | Chaos / resilience testing | ⬜ Planned |
| 12 | Deployment & backups | ⬜ Planned |

---

## What's built so far (Phases 0–3)

**Phase 0 — Scaffolding.** Monorepo layout, tooling checks, Spring Cloud service skeletons.

**Phase 1 — Foundation + live market data.**
- Spring Cloud stack: Eureka (service discovery), Config Server, API Gateway.
- Market Data Service ingests **live crypto data** over a secure `wss://` websocket into **TimescaleDB** (time-series), with **Redis** as a cache.
- Hardened: websocket auto-reconnect with backoff, graceful degradation (a Redis outage does not take the service down — reads fall back to TimescaleDB), batched inserts, retention policy.
- Security: secure feed, CI vulnerability scanning (Trivy, OWASP Dependency-Check, secret scanning), no tracked secrets.

**Phase 2 — Matching engine + execution.**
- **Matching engine** (C++17, `services/matching-engine/`), four layers:
  - *Order book* — price-time priority using ordered maps per side, FIFO queues per level, and an index for O(1) cancels. Integer tick prices (no floating point).
  - *Matching* — limit & market orders, partial fills, multi-level sweeps, maker-price execution.
  - *Crash recovery* — event sourcing: a write-ahead append-only log; the book is rebuilt by replaying the log on startup.
  - *gRPC server* — exposes the engine over the network (`ledgerbull.api.MatchingEngine`), reflection enabled.
- **Execution Service** (Java/Spring Boot) — validates orders, submits valid ones to the C++ engine over gRPC, returns fills. Eureka-registered.

**Phase 3 — Execution lifecycle.**
- **PostgreSQL persistence** (separate from the market-data TimescaleDB) for orders and fills, with Flyway-managed schema migrations.
- **Order state machine** — NEW → PARTIALLY_FILLED → FILLED, plus CANCELLED and REJECTED, with enforced valid transitions.
- **Save-on-accept** — every accepted order is recorded before the engine call; an unreachable/rejecting engine leaves the order as REJECTED, so no accepted order is ever silently lost.
- **Both-sides tracking** — when a taker order matches resting maker orders, both the taker and the makers have their status and quantities updated, with maker fills accumulating correctly across multiple matches.
- **Query & cancel** — `GET /orders/{id}` (with fills), `GET /orders?symbol=&status=` (filtered, paginated), and a guarded cancel endpoint (only NEW/PARTIALLY_FILLED → CANCELLED). Prices are returned human-readable; stored as integer ticks.

**Phase 4 — Positions & PnL.**
- **Dedicated position service** with its own PostgreSQL database, separate from execution — the parts stay decoupled (execution records *what happened*; positions derive *what you now hold and what it's worth*).
- **Idempotent fill ingestion** — pulls fills from the execution service and records each exactly once (a `processed_fills` guard prevents double-counting on retries), so positions never drift.
- **Signed net positions** — net quantity per symbol (positive = long, negative = short), attributed to the taker side of each trade.
- **Realized PnL via FIFO lot accounting** — each buy opens a lot (quantity at a cost price); each sell consumes the oldest lots first, and realized PnL = (sell price − lot price) × quantity. Recompute replays all fills from scratch and is idempotent.
- **Unrealized PnL from the live price** — the "paper" PnL on open lots, computed at read time as (current price − lot price) × remaining quantity. The current price is read from the Redis latest-price cache. It is never stored (it changes every tick), and it fails cleanly to `null` (never a misleading 0) if the price is unavailable.
- **Money as integer ticks** — all money is stored and computed as scaled integers (no floating-point drift), converted to human-readable decimals only at the boundary.
- **Query endpoints** — `GET /api/positions` (all symbols) and `GET /api/positions/{symbol}` return net quantity, realized PnL, and unrealized PnL together (with human-readable forms), both routed through one shared builder so the list and single-symbol responses never diverge.

---

## Tech stack

| Layer | Technology | Status |
|-------|------------|--------|
| Backend / microservices | Java 21 + Spring Boot 3.x | ✅ In use |
| Matching engine core | C++17 (STL, gRPC/protobuf) | ✅ In use |
| Service mesh | Spring Cloud (Eureka, Gateway, Config Server) | ✅ In use |
| Inter-service (engine) | gRPC + Protocol Buffers | ✅ In use |
| Relational DB | PostgreSQL 16 (orders, fills; separate DB for positions, lots) | ✅ In use |
| Time-series DB | TimescaleDB (market data) | ✅ In use |
| Cache | Redis (latest price; feeds unrealized PnL) | ✅ In use |
| Migrations | Flyway | ✅ In use |
| Containerization | Docker + Docker Compose | ✅ In use |
| CI/CD | GitHub Actions (build, security scan, lint) | ✅ In use |
| Frontend | Next.js (App Router) + TypeScript + Tailwind | 🚧 Scaffolded |
| AI / ML | Python (signals, risk, anomaly detection) | ⬜ Planned (Phase 6) |
| Resilience | Resilience4j | ⬜ Planned (Phase 9) |
| Observability | Prometheus, Grafana, Zipkin, Loki | ⬜ Planned (Phase 10) |
| Scaling | Kubernetes (Minikube) + Helm | ⬜ Planned |

Items marked ⬜ are planned for later phases and are **not yet implemented** — listed to show the intended architecture.

---

## Architecture (matching engine)

The matching engine is a single C++ service with four stacked layers. An order flows down; fills flow back up:

```
Client (Execution Service, Java)
        │  gRPC
        ▼
┌───────────────────────────────────────────┐
│  C++ matching engine (services/matching-engine)
│  ┌─────────────────────────────────────┐  │
│  │ Layer 4 — gRPC server               │  │  thin adapter, delegates
│  ├─────────────────────────────────────┤  │
│  │ Layer 3 — event log (write-ahead)   │  │  append to disk, replay on start
│  ├─────────────────────────────────────┤  │
│  │ Layer 2 — matching logic            │  │  cross, fill, sweep, maker price
│  ├─────────────────────────────────────┤  │
│  │ Layer 1 — order book                │  │  price-time priority, O(1) cancel
│  └─────────────────────────────────────┘  │
└───────────────────────────────────────────┘
```

The in-memory order book is a fast, rebuildable view; the append-only event log on disk is the source of truth (event sourcing). No traditional database on the matching hot path — a deliberate latency choice. The surrounding services use real databases (PostgreSQL for orders/fills, TimescaleDB for market data).

---

## Repository layout

```
.
├── docs/adr/                      # Architecture Decision Records
├── infra/                         # docker-compose, k8s manifests (later)
├── services/
│   ├── eureka-server/             # service discovery          [built]
│   ├── config-server/             # centralized config         [built]
│   ├── api-gateway/               # Spring Cloud Gateway        [built]
│   ├── market-data-service/       # live market data ingest     [built]
│   ├── matching-engine/           # C++ order book + matching   [built]
│   ├── execution-service/         # validation, persistence,    [built]
│   │                              #   state machine, query/cancel
│   ├── position-service/         # positions, FIFO PnL, PnL    [built]
│   ├── risk-service/              # risk engine + Python        [planned]
│   ├── strategy-signal-service/   # signals + Python           [planned]
│   └── alert-service/             # alerts                     [planned]
├── frontend/                      # Next.js dashboard          [scaffolded]
└── README.md
```

---

## Getting started

**Prerequisites:** Java 21, Maven, Docker, Docker Compose, Git. For the matching engine: CMake and a C++17 compiler, plus gRPC + protobuf (`brew install grpc protobuf`).

**1. Start datastores:**
```bash
cd infra && docker compose up -d
```

**2. Start the Spring Cloud services** (each in its own terminal):
```bash
cd services/eureka-server       && mvn spring-boot:run   # 8761
cd services/config-server       && mvn spring-boot:run   # 8888
cd services/api-gateway         && mvn spring-boot:run   # 8080
cd services/market-data-service && mvn spring-boot:run   # 8081
```

**3. Build & run the matching engine:**
```bash
cd services/matching-engine
cmake -S . -B build -DCMAKE_PREFIX_PATH="$(brew --prefix)"
cmake --build build
./build/matching_engine_server                            # 50051
```

**4. Run the execution service:**
```bash
cd services/execution-service && mvn spring-boot:run      # 8082
```

**5. Run the position service:**
```bash
cd services/position-service && mvn spring-boot:run       # 8083
```

**6. Try it:**
```bash
# Resting sell
curl -X POST http://localhost:8082/api/execution/orders -H "Content-Type: application/json" \
  -d '{"order_id":"1","symbol":"BTC-USD","side":"SELL","type":"LIMIT","price":105,"quantity":5}'
# Crossing buy — returns a fill; both orders become FILLED
curl -X POST http://localhost:8082/api/execution/orders -H "Content-Type: application/json" \
  -d '{"order_id":"2","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":105,"quantity":5}'
# Query the order and its fills
curl http://localhost:8082/api/execution/orders/2

# Pull fills into the position service, recompute, then view positions & PnL
curl -X POST http://localhost:8083/api/positions/ingest-fills
curl -X POST http://localhost:8083/api/positions/recompute
# net quantity, realized PnL (FIFO), and unrealized PnL (live price) for one symbol
curl http://localhost:8083/api/positions/BTC-USD
# or all symbols at once
curl http://localhost:8083/api/positions
```

---

## Design notes & honest scope

- **Real data, simulated execution.** Crypto uses live real-time data; equities (later phases) will use free delayed data as a simulation. No real trades are executed — that requires broker-dealer licensing and is out of scope.
- **Deliberately deferred to later phases:** TLS/mTLS + auth + rate limiting (Phase 8), circuit breakers + failover (Phase 9), observability (Phase 10), backups (Phase 12).
- **Known constraints (free single-VM deployment):** the matching engine is currently single-threaded; there is no true high-availability failover (that needs paid multi-node infrastructure); exchange-grade microsecond latency is not a goal on shared free infrastructure. These are documented deliberate trade-offs, not oversights.