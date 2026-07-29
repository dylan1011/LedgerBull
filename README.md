# LedgerBull

**LedgerBull** is a real-time trading and risk platform. It takes in live market data, checks and matches orders with a fast engine, tracks each order from start to finish, and works out your positions and profit or loss. Later phases will add risk checks. It is built as separate small services in C++, Java, and (later) Python.

> **Scope note:** This is a demo platform built on free tools. It uses real market data and a real matching engine, but it does **not** place real trades (real trades need a broker license). The goal is to show good engineering and the real shape of a trading system.

---

## Status

**Done and tested up to Phase 4.** The full path works end to end: live price comes in, the order is checked, the C++ engine matches it, the order and fills are saved, the order state is tracked on both sides, you can query and cancel it, and then positions, realized profit (FIFO), and unrealized profit (live price) are worked out and can be queried.

| Phase | What it covers | Status |
|-------|----------------|--------|
| 0 | Project setup and tooling | ✅ Done |
| 1 | Spring Cloud base + live market data | ✅ Done |
| 2 | Matching engine (order book, matching, crash recovery, gRPC) + execution | ✅ Done |
| 3 | Order lifecycle — state machine, saving, query and cancel | ✅ Done |
| 4 | Positions and profit (net position, FIFO realized PnL, live unrealized PnL, query endpoints) | ✅ Done |
| 5 | Risk engine | ⬜ Planned |
| 6 | AI/ML signals (Python) | ⬜ Planned |
| 7 | Full pipeline (data → decision → execution → risk) | ⬜ Planned |
| 8 | Security hardening (TLS/mTLS, auth, rate limiting) | ⬜ Planned |
| 9 | Reliability (circuit breakers, failover, graceful fallback) | ⬜ Planned |
| 10 | Observability (metrics, tracing, logs, alerts) | ⬜ Planned |
| 11 | Chaos / resilience testing | ⬜ Planned |
| 12 | Deployment and backups | ⬜ Planned |

---

## Documentation

Full documents live in the [`docs/`](docs/) folder:

- **LedgerBull.docx** — the complete project document: concept, problem, proposed solution, full system design and architecture (with a color-coded diagram), data model, API reference, PnL math, key design decisions, cost, roadmap, and glossary.
- **LedgerBull-Development-Story-and-Issues.docx** — the build story phase by phase, every real bug faced and how it was fixed, and an FAQ of common questions about the project.

---

## Tools and technologies

**Languages and frameworks**
- **C++17** — the matching engine core (fast, no garbage collector on the hot path).
- **Java 21 + Spring Boot 3.x** — the web services (execution, positions, market data, gateway).
- **Python** — planned later for AI/ML signals and risk.

**Data**
- **PostgreSQL 16** — saves orders and fills; a separate database saves positions and lots.
- **TimescaleDB** — stores time-series market data.
- **Redis** — cache for the latest price; the position service reads it for unrealized PnL.
- **Flyway** — runs database schema migrations.

**Service communication**
- **gRPC + Protocol Buffers** — fast, typed link between the Java services and the C++ engine.
- **Spring Cloud** — Eureka (service discovery), Config Server, API Gateway.

**Build and run**
- **Docker + Docker Compose** — run the databases and services in containers.
- **Maven** — build the Java services.
- **CMake** — build the C++ engine.

**CI/CD — GitHub Actions**
Every push runs two workflows:
- **Lint & Static Analysis**
  - **cppcheck** — static analysis for the C++ engine.
  - **SpotBugs** — static analysis for the Java code.
  - **ESLint** — lint for the frontend.
- **Security Scan**
  - **Trivy** — scans the Docker image and files for known vulnerabilities (CVEs).
  - **OWASP Dependency-Check** — checks Java dependencies for known CVEs.
  - **TruffleHog** — scans for leaked secrets (API keys, passwords).

**Planned for later phases** (listed to show the plan, not yet built): Resilience4j (Phase 9); Prometheus, Grafana, Zipkin, Loki (Phase 10); Kubernetes + Helm (scaling). A Next.js + TypeScript + Tailwind frontend is scaffolded.

---

## A note on the CI checks (please read)

Every push runs the two workflows above. In the run history you will see some old runs marked red. Here is the honest reason, in simple words:

- Old runs are **frozen**. They ran on the code as it was at that time, so they cannot turn green now.
- The red runs came from two things: a few C++ static-analysis **style hints** (now fixed), and known **CVEs inside the base image** `eclipse-temurin:21-jre` (the Java runtime image), not my own code.
- The base-image CVEs **cannot be fixed by me directly** — only by the upstream image being rebuilt. So they are listed, with a reason and a date, in [`.trivyignore`](.trivyignore). This is normal, accepted practice for vulnerabilities you cannot patch yourself.
- Trivy scans the **whole image on every push**, so even a docs-only commit will re-report the same base-image CVEs.
- **My own code and my own dependencies scan clean.** The findings are all upstream.

Later, in Phase 8 (security hardening), I plan to move to a smaller base image (distroless/chiselled) that ships only the Java runtime and the app. That removes most of these base-image CVEs at the source.

---

## What is built so far

**Phase 0 — Setup.** Monorepo layout, tooling checks, Spring Cloud service skeletons.

**Phase 1 — Base + live market data.**
- Spring Cloud stack: Eureka (service discovery), Config Server, API Gateway.
- The Market Data Service takes in **live crypto prices** over a secure `wss://` websocket and stores them in **TimescaleDB**, with **Redis** as a cache.
- Made robust: the websocket reconnects with backoff; if Redis is down the service still works (it reads from TimescaleDB); inserts are batched; old data is cleaned up.
- Security from day one: secure feed, CI vulnerability scanning (Trivy, OWASP, secret scan), no secrets in the repo.

**Phase 2 — Matching engine + execution.**
- **Matching engine** (C++17, `services/matching-engine/`), in four layers:
  - *Order book* — price-time priority, ordered maps per side, FIFO queue per price level, and an index for O(1) cancels. Prices are integer ticks (no floating point).
  - *Matching* — limit and market orders, partial fills, multi-level sweeps, maker-price execution.
  - *Crash recovery* — event sourcing: an append-only write-ahead log; the book is rebuilt by replaying the log on startup.
  - *gRPC server* — exposes the engine on the network (`ledgerbull.api.MatchingEngine`).
- **Execution Service** (Java/Spring Boot) — checks orders, sends valid ones to the C++ engine over gRPC, returns fills. Registered in Eureka.

**Phase 3 — Order lifecycle.**
- **PostgreSQL** (separate from the market-data TimescaleDB) saves orders and fills, with Flyway migrations.
- **Order state machine** — NEW → PARTIALLY_FILLED → FILLED, plus CANCELLED and REJECTED, with only valid moves allowed.
- **Save first** — every accepted order is saved before the engine call, so if the engine is down or rejects, the order is REJECTED and never silently lost.
- **Both sides tracked** — when a taker order hits resting maker orders, both the taker and the makers get their status and quantities updated, and maker fills add up correctly across many matches.
- **Query and cancel** — `GET /orders/{id}` (with fills), `GET /orders?symbol=&status=` (filtered, paged), and a guarded cancel (only NEW/PARTIALLY_FILLED → CANCELLED). Prices are shown human-readable; stored as integer ticks.

**Phase 4 — Positions and profit.**
- **Own position service** with its own PostgreSQL database, separate from execution. The parts stay independent: execution records *what happened*; positions work out *what you hold and what it is worth*.
- **Safe fill ingestion** — pulls fills from the execution service and records each one only once (a `processed_fills` guard stops double-counting on retries), so positions never drift.
- **Signed net position** — net quantity per symbol (positive = long, negative = short), taken from the taker side of each trade.
- **Realized profit with FIFO lots** — each buy opens a lot (quantity at a cost price); each sell uses the oldest lots first, and realized PnL = (sell price − lot price) × quantity. Recompute replays all fills from scratch and gives the same result every time.
- **Unrealized profit from the live price** — the "paper" profit on lots you still hold, worked out at read time as (current price − lot price) × remaining quantity. The current price is read from the Redis latest-price cache. It is never stored (it changes every tick), and if the price is missing it returns `null`, never a wrong 0.
- **Money as integer ticks** — all money is stored and computed as whole numbers (no floating-point drift), turned into decimals only for display.
- **Query endpoints** — `GET /api/positions` (all symbols) and `GET /api/positions/{symbol}` return net quantity, realized PnL, and unrealized PnL together (with human-readable forms). Both go through one shared builder, so the list and the single-symbol response always match.

---

## Architecture (matching engine)

The matching engine is one C++ service with four stacked layers. An order flows down; fills flow back up:

```
Client (Execution Service, Java)
        │  gRPC
        ▼
┌───────────────────────────────────────────┐
│  C++ matching engine (services/matching-engine)
│  ┌─────────────────────────────────────┐  │
│  │ Layer 4 — gRPC server               │  │  thin adapter, passes work down
│  ├─────────────────────────────────────┤  │
│  │ Layer 3 — event log (write-ahead)   │  │  append to disk, replay on start
│  ├─────────────────────────────────────┤  │
│  │ Layer 2 — matching logic            │  │  cross, fill, sweep, maker price
│  ├─────────────────────────────────────┤  │
│  │ Layer 1 — order book                │  │  price-time priority, O(1) cancel
│  └─────────────────────────────────────┘  │
└───────────────────────────────────────────┘
```

The in-memory order book is a fast view that can be rebuilt; the append-only event log on disk is the source of truth (event sourcing). There is no normal database on the matching hot path — this is a deliberate choice for speed. The services around it use real databases (PostgreSQL for orders/fills, TimescaleDB for market data).

---

## Repository layout

```
.
├── docs/
│   ├── LedgerBull.docx                          # complete project document (+ architecture diagram)
│   ├── LedgerBull-Development-Story-and-Issues.docx  # build story, bugs & fixes, FAQ
│   └── adr/                       # Architecture Decision Records
├── infra/                         # docker-compose, k8s manifests (later)
├── services/
│   ├── eureka-server/             # service discovery          [built]
│   ├── config-server/             # central config             [built]
│   ├── api-gateway/               # Spring Cloud Gateway        [built]
│   ├── market-data-service/       # live market data ingest     [built]
│   ├── matching-engine/           # C++ order book + matching   [built]
│   ├── execution-service/         # checks, saving,             [built]
│   │                              #   state machine, query/cancel
│   ├── position-service/          # positions, FIFO PnL, PnL    [built]
│   ├── risk-service/              # risk engine + Python        [planned]
│   ├── strategy-signal-service/   # signals + Python           [planned]
│   └── alert-service/             # alerts                     [planned]
├── frontend/                      # Next.js dashboard          [scaffolded]
├── .trivyignore                   # documented CVE suppressions
└── README.md
```

---

## Getting started

**You need:** Java 21, Maven, Docker, Docker Compose, Git. For the matching engine: CMake and a C++17 compiler, plus gRPC + protobuf (`brew install grpc protobuf`).

**1. Start the databases:**
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

**3. Build and run the matching engine:**
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

# Pull fills into the position service, recompute, then view positions and PnL
curl -X POST http://localhost:8083/api/positions/ingest-fills
curl -X POST http://localhost:8083/api/positions/recompute
# net quantity, realized PnL (FIFO), and unrealized PnL (live price) for one symbol
curl http://localhost:8083/api/positions/BTC-USD
# or all symbols at once
curl http://localhost:8083/api/positions
```

---

## Design notes and honest scope

- **Real data, simulated execution.** Crypto uses live real-time data; equities (later) will use free delayed data as a simulation. No real trades are placed — that needs a broker license and is out of scope.
- **Left for later phases on purpose:** TLS/mTLS + auth + rate limiting (Phase 8); circuit breakers + failover (Phase 9); observability (Phase 10); backups (Phase 12).
- **Known limits (free single-VM setup):** the matching engine is single-threaded for now; there is no true high-availability failover (that needs paid multi-node infra); exchange-grade microsecond latency is not a goal on shared free infra. These are clear, on-purpose trade-offs, not mistakes.