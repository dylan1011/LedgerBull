# LedgerBull

**LedgerBull** is a real-time trading and risk platform. It ingests live market data, turns it into trade decisions, executes them, and continuously monitors financial risk — all in parallel.

> **Environment:** macOS Tahoe (Apple Silicon, Homebrew at `/opt/homebrew`)

---

## Tech Stack

| Layer | Technology | Role |
| --- | --- | --- |
| Backend / microservices | **Java 21 + Spring Boot 3.x** | The core spine |
| Matching engine core | **C++** | Low-latency order book |
| AI / ML | **Python** | Signals, risk forecasting, anomaly detection, RL execution, LLM analytics |
| Frontend | **JavaScript (React / Next.js)** | Dashboard |
| Database | **PostgreSQL + TimescaleDB** (Redis for caching) | ACID + time-series |
| Containerization | **Docker + Docker Compose** | Primary runtime |
| Scaling layer | **Kubernetes (Minikube local) + Helm** | Demonstrated scaling |
| Service mesh (Spring Cloud) | **Eureka, Gateway, Config Server** | Discovery, routing, config |
| Resilience | **Resilience4j** | Circuit breaker, retry, bulkhead, rate limiter |
| CI/CD | **GitHub Actions** | Build & deploy pipelines |
| Observability | **Prometheus + Grafana + Zipkin + Loki** | Metrics, dashboards, tracing, logs |
| Hosting | **Oracle Cloud Always Free** (backend), **Vercel** (frontend), **Cloudflare** (edge/backups) | Deployment targets |

---

## Repository Layout

```
.
├── docs/
│   └── adr/                          # Architecture Decision Records
├── infra/
│   ├── docker-compose.yml            # local orchestration (created later)
│   └── k8s/                          # Kubernetes manifests (created later)
├── services/
│   ├── eureka-server/                # Spring Boot — service discovery
│   ├── config-server/                # Spring Boot — centralized config
│   ├── api-gateway/                  # Spring Cloud Gateway
│   ├── market-data-service/          # Spring Boot — ingest/normalize market data
│   ├── matching-engine/              # C++ — low-latency order book (built later)
│   ├── execution-service/            # Spring Boot — order lifecycle
│   ├── position-pnl-service/         # Spring Boot — positions & PnL
│   ├── risk-service/                 # Spring Boot (orch) + Python sidecar
│   ├── strategy-signal-service/      # Spring Boot (orch) + Python sidecar
│   └── alert-service/                # Spring Boot — alerts
├── ai-sidecar/                       # Python FastAPI — ML/AI serving
├── frontend/                         # React / Next.js dashboard
├── .gitignore
└── README.md
```

---

## Getting Started

> **Status:** Phase 0 (scaffolding) + start of Phase 1 (Spring Cloud foundation).

### Prerequisites

Required: **Java 21**, **Maven**, **Docker**, **Docker Compose**, **Git**.
Needed later: Python, Node, g++, cmake, Kubernetes tools (Minikube, kubectl, Helm).

Verify your environment:

```bash
bash check-tools.sh
```

> On this machine, Java 21 lives at `/opt/homebrew/opt/openjdk@21` (keg-only). To use it:
>
> ```bash
> export JAVA_HOME="/opt/homebrew/opt/openjdk@21"
> export PATH="$JAVA_HOME/bin:$PATH"
> ```

### Spring Cloud foundation

Start the infrastructure services (each in its own terminal):

```bash
# 1. Service discovery — http://localhost:8761
cd services/eureka-server && mvn spring-boot:run

# 2. Centralized config — http://localhost:8888
cd services/config-server && mvn spring-boot:run

# 3. API gateway — http://localhost:8080 (registers with Eureka)
cd services/api-gateway && mvn spring-boot:run
```

Then open the Eureka dashboard at <http://localhost:8761> and confirm `API-GATEWAY` is registered.

---

## Roadmap

- **Phase 0** — Project scaffolding ✅
- **Phase 1** — Spring Cloud foundation (Eureka, Config Server, Gateway) 🚧
- **Phase 2+** — Business services (market data, matching engine, execution, position/PnL, risk, strategy/signal, alerts), AI sidecar, frontend, observability, and deployment.
