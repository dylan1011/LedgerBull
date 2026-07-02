# ADR-001: Core Technology Stack

- **Status:** Accepted
- **Date:** 2026-07-02
- **Deciders:** LedgerBull core team

## Context

LedgerBull is a real-time trading and risk platform that must ingest live market
data, generate trade decisions, execute orders, and continuously monitor financial
risk — concurrently and with low latency. The stack must balance raw performance
(matching engine), a mature and productive services ecosystem (business logic),
a rich data-science ecosystem (AI/ML), and a modern web experience (dashboard),
while remaining cheap to host and easy to operate locally.

This ADR records the primary technology choices and the rationale behind each.

## Decision

### Java 21 + Spring Boot 3.x — microservices backbone
The core spine of the platform is built on Spring Boot. Java brings a mature,
battle-tested ecosystem for building resilient distributed systems, first-class
Spring Cloud integration (discovery, config, gateway), strong tooling, and a huge
talent pool. Java 21 gives us virtual threads (Project Loom) for high-concurrency
I/O, records, pattern matching, and long-term LTS support. Spring Boot 3.x targets
Java 17+ and integrates cleanly with the rest of the Spring Cloud family we rely on.

### C++ — matching engine core
The order-matching engine is the most latency-sensitive component. C++ gives us
deterministic, low-latency execution with **no garbage-collection pauses**, precise
control over memory layout and cache behavior, and direct access to lock-free data
structures. This is exactly where the JVM's convenience is a liability, so we isolate
this hot path in native code and expose it to the rest of the system via a thin
boundary.

### Python — AI / ML
All AI/ML workloads (signal generation, risk forecasting, anomaly detection,
reinforcement-learning execution, and LLM-driven analytics) live in Python because
that is where the ecosystem is: NumPy/pandas, PyTorch/TensorFlow, scikit-learn,
statsmodels, and the entire modern LLM tooling stack. Python serving runs as a
FastAPI sidecar alongside the Java orchestration services rather than being embedded
in them, keeping the ML runtime and the JVM runtime independently deployable.

### JavaScript (React / Next.js) — frontend
The trading dashboard is built with React/Next.js. React is the industry standard for
rich, real-time UIs; Next.js adds routing, server-side rendering, and a first-class
deployment story on Vercel. This gives us a fast, modern, component-driven dashboard
with excellent developer ergonomics.

### PostgreSQL + TimescaleDB — data (Redis for caching)
Trading data is inherently time-series (ticks, bars, PnL over time) but also relational
(accounts, orders, positions). **TimescaleDB is a PostgreSQL extension**, so we get
ACID transactions, SQL, and the mature Postgres ecosystem *and* high-performance
time-series capabilities (hypertables, continuous aggregates, compression) in a
**single engine** — avoiding the operational cost of running a separate time-series
database. Redis is used for low-latency caching and ephemeral hot state.

### Docker + Docker Compose — primary runtime
Docker Compose is the primary local and demo runtime. It orchestrates the full
multi-language system (Java services, C++ engine, Python sidecar, databases, and
observability stack) with a single declarative file, making the platform trivial to
spin up and tear down on a developer machine.

### Kubernetes (Minikube local) + Helm — demonstrated scaling layer
Kubernetes is included as the **demonstrated scaling layer** rather than the primary
runtime. Minikube + Helm let us show that the system can scale horizontally, roll out
safely, and run in a production-grade orchestrator, without imposing K8s complexity on
everyday development.

### Eureka — service discovery in the Compose runtime
Within the Docker Compose runtime, services find each other via Netflix Eureka. It
integrates natively with Spring Cloud, requires minimal configuration, and provides a
clear dashboard of registered instances — a good fit for the Compose-based runtime
where we are not relying on Kubernetes-native service discovery.

## Consequences

- **Polyglot complexity:** we accept the operational overhead of running Java, C++, and
  Python together, mitigated by containerization and clear service boundaries.
- **Clear separation of concerns:** each language is used where it is strongest, so no
  single component is forced into a poor-fit runtime.
- **Two runtimes to reason about:** Docker Compose (primary) and Kubernetes
  (demonstrated). We keep manifests/Helm charts in sync as a deliberate, bounded cost.
- **Single data engine:** using TimescaleDB-on-Postgres reduces the number of stateful
  systems to operate, at the cost of coupling time-series and relational workloads to
  one engine.
