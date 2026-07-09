# LedgerBull

**LedgerBull** is a real-time trading & risk platform: it ingests live market data, turns it into order flow, matches orders through a low-latency engine, and (in later phases) monitors risk continuously — built as parallel microservices across Java, C++, Python, and Next.js.

> **Note:** This is a demonstration platform built on free-tier infrastructure. It uses real market data and a genuine matching engine, but it does **not** execute real trades (that would require broker-dealer licensing). It's designed to demonstrate production-grade engineering practices, not to operate as a live exchange.

> **Environment:** macOS (Apple Silicon, Homebrew). Hosting targets: Oracle Cloud Always Free (backend), Vercel (frontend), Cloudflare (edge/backups).

---

## Status

**Completed and verified through Phase 2** — the full trading core works end to end: live market data in → order validated → matched by the C++ engine → fill returned, crash-safe and networked across two languages.

| Phase | Scope | Status |
|-------|-------|--------|
| 0 | Project scaffolding | ✅ Complete |
| 1 | Spring Cloud foundation + live market data (hardened, secured) | ✅ Complete |
| 2 | Matching engine (order book, matching, crash recovery, gRPC) + execution service | ✅ Complete |
| 3 | Execution lifecycle (order state management) | ⬜ Planned |
| 4 | Position & PnL tracking | ⬜ Planned |
| 5 | Risk engine | ⬜ Planned |
| 6 | AI/ML signals (Python sidecar) | ⬜ Planned |
| 7 | Integration (full data → decision → execution → risk pipeline) | ⬜ Planned |
| 8 | Security hardening (TLS/mTLS, auth, rate limiting) | ⬜ Planned |
| 9 | Reliability (circuit breakers, failover, graceful degradation) | ⬜ Planned |
| 10 | Observability (metrics, tracing, logs, alerting) | ⬜ Planned |
| 11 | Chaos / resilience testing | ⬜ Planned |
| 12 | Deployment & backups | ⬜ Planned |

---

## What's built so far (Phases 0–2)

**Phase 0 — Scaffolding.** Monorepo layout, tooling checks, Spring Cloud service skeletons.

**Phase 1 — Foundation + live market data.**
- Spring Cloud stack: Eureka (service discovery), Config Server, API Gateway.
- Market Data Service ingests **live crypto data** over a secure `wss://` websocket into **TimescaleDB** (time-series), with **Redis** as a cache.
- Hardened: websocket auto-reconnect with backoff, graceful degradation (a Redis outage does not take the service down — reads fall back to TimescaleDB), batched inserts, data retention policy, auto-restart.
- Security: secure feed, CI vulnerability scanning (Trivy, OWASP Dependency-Check, secret scanning), no tracked secrets.

**Phase 2 — Matching engine + execution.**
- **Matching engine** (C++17, in `services/matching-engine/`), built as four layers:
  - *Order book* — price-time priority using ordered maps per side, FIFO queues per price level, and an index for O(1) cancels. Integer tick prices (no floating point).
  - *Matching* — limit & market orders, partial fills, multi-level sweeps, maker-price execution, price-time priority.
  - *Crash recovery* — event sourcing: a write-ahead append-only log on disk; the book is rebuilt by replaying the log on startup.
  - *gRPC server* — exposes the engine over the network (service `ledgerbull.api.MatchingEngine`), with reflection enabled.
- **Execution Service** (Java/Spring Boot) — validates orders (rejecting invalid ones before they reach the engine), submits valid ones to the C++ engine over gRPC, and returns the resulting fills. Exposes REST endpoints and registers with Eureka.
- Verified end to end: an order submitted via REST is validated in Java, matched by the C++ engine, and the fill returned — crash-safe and cross-language.

---

## Tech stack

| Layer | Technology | Status |
|-------|------------|--------|
| Backend / microservices | Java 21 + Spring Boot 3.x | ✅ In use |
| Matching engine core | C++17 (STL, gRPC/protobuf) | ✅ In use |
| Service mesh | Spring Cloud (Eureka, Gateway, Config Server) | ✅ In use |
| Database | PostgreSQL + TimescaleDB, Redis (cache) | ✅ TimescaleDB + Redis in use |
| Inter-service (engine) | gRPC + Protocol Buffers | ✅ In use |
| Containerization | Docker + Docker Compose | ✅ In use |
| CI/CD | GitHub Actions (build, security scan, lint) | ✅ In use |
| Frontend | Next.js (App Router) + TypeScript + Tailwind | 🚧 Scaffolded |
| AI / ML | Python (signals, risk, anomaly detection) | ⬜ Planned (Phase 6) |
| Resilience | Resilience4j | ⬜ Planned (Phase 9) |
| Observability | Prometheus, Grafana, Zipkin, Loki | ⬜ Planned (Phase 10) |
| Scaling | Kubernetes (Minikube) + Helm | ⬜ Planned |

Items marked ⬜ are planned for later phases and are **not yet implemented** — listed here to show the intended architecture.

---

## Architecture (matching engine)

The matching engine is a single C++ service with four stacked layers. An order flows down through them; fills flow back up:

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

The in-memory order book is a fast, rebuildable view; the append-only event log on disk is the source of truth (event sourcing). No traditional database on the matching hot path — this is a deliberate latency choice.

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
│   ├── execution-service/         # order validation + submit   [built]
│   ├── position-pnl-service/      # positions & PnL            [planned]
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
cd services/eureka-server     && mvn spring-boot:run   # 8761
cd services/config-server     && mvn spring-boot:run   # 8888
cd services/api-gateway       && mvn spring-boot:run   # 8080
cd services/market-data-service && mvn spring-boot:run # 8081
```

**3. Build & run the matching engine:**
```bash
cd services/matching-engine
cmake -S . -B build -DCMAKE_PREFIX_PATH="$(brew --prefix)"
cmake --build build
./build/matching_engine_server                          # 50051
```

**4. Run the execution service:**
```bash
cd services/execution-service && mvn spring-boot:run    # 8082
```

**5. Try it — submit orders:**
```bash
# Resting sell
curl -X POST http://localhost:8082/api/execution/orders -H "Content-Type: application/json" \
  -d '{"order_id":"1","symbol":"BTC-USD","side":"SELL","type":"LIMIT","price":105,"quantity":5}'
# Crossing buy — returns a fill
curl -X POST http://localhost:8082/api/execution/orders -H "Content-Type: application/json" \
  -d '{"order_id":"2","symbol":"BTC-USD","side":"BUY","type":"LIMIT","price":105,"quantity":5}'
```

---

## Design notes & honest scope

- **Real data, simulated execution.** Crypto uses live real-time data; equities (later phases) will use free delayed data as a simulation. No real trades are executed — that requires broker-dealer licensing and is out of scope.
- **Deliberately deferred to later phases:** TLS/mTLS + auth + rate limiting (Phase 8), circuit breakers + failover (Phase 9), observability (Phase 10), backups (Phase 12).
- **Known constraints (free single-VM deployment):** the matching engine is currently single-threaded; there is no true high-availability failover (that needs paid multi-node infrastructure); exchange-grade microsecond latency is not a goal on shared free infrastructure. These are documented deliberate trade-offs, not oversights.