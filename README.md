# SwiftPay

> **Poly-repo, event-driven payment platform — 250 TPS sustained verified (k6 + PCAP), 0 failures, exactly-once-effective, independently deployable.**

SwiftPay is three Spring Boot microservices on **Java 25** that move money the way real payment systems do: a thin synchronous HTTP gateway accepts a request, an asynchronous Kafka backbone settles it, and a read-side projection serves analytics queries. Each service is its own repo, with its own Gradle root, Dockerfile, and CI pipeline — independently buildable and independently deployable.

---

## Highlights

| | |
|---|---|
| 🚀 **Throughput** | **250 TPS sustained** at the gateway, verified by k6 (`sustained_250_tps  250.00 iters/s`) — **0 HTTP failures, 100 % accepted**, k6 p(50) = 1.89 ms, p(99) = 8.42 ms across **57 899** requests. |
| 🛡️ **Zero event loss** | Transactional outbox on the producer + consumer inbox on the consumer = at-least-once delivery + exactly-once side effects. |
| 🔁 **Self-healing pipeline** | Kafka `@RetryableTopic` → retry → DLT, outbox PUBLISHING-state rescue sweep, AFTER_COMMIT cache invalidation. |
| 🧱 **True poly-repo** | Each service owns its contracts. Cross-service Kafka uses logical wire names, not Java FQNs. |
| 🐳 **One-command stack** | `docker compose up -d --build` brings up Kafka (KRaft), all three services, and Kafka UI. |
| 🤖 **Per-service CI** | GitHub Actions per repo with path-scoped triggers, Gradle caching, and Docker layer caching. |
| 📈 **Network evidence** | Real k6 load tests + **PCAP capture** across HTTP, Kafka, Postgres, Redis ports — 5.2 M packets captured in the validated run, see `loadtest/DRYRUN_REPORT.md`. |

---

## Architecture

```
                                  ┌────────────────────────────────────┐
                                  │                KAFKA                │
                                  │   (KRaft, no Zookeeper)             │
                                  │                                     │
                                  │  payment-initiated                  │
                                  │  payment-initiated-retry-1000       │
                                  │  payment-initiated-retry-2000       │
                                  │  payment-initiated-dlt              │
                                  │  payment-completed                  │
                                  │  payment-failed                     │
                                  └─────────▲────────────────▲──────────┘
                                            │                │
                       publish payment-initiated         consume payment-completed
                                            │                │
   ┌──────────────────┐                     │                │       ┌──────────────────┐
   │ HTTP client      │ ──POST /v1/payments─┼──────────┐     │       │ HTTP client      │
   │ (your test, app) │                     │          │     │       │ (dashboards)     │
   └──────────────────┘                     │          │     │       └────────┬─────────┘
                                            │          │     │                │
                                            │          │     │                │
                                  ┌─────────┴────────┐ │     │       ┌────────▼─────────┐
                                  │ transaction-      │ │     │       │ analytics-worker │
                                  │   gateway (8081)  │ │     │       │  (8083)          │
                                  │                   │ │     │       │                  │
                                  │ ⊕ outbox relay    │ │     │       │ ⊕ inbox dedup    │
                                  │ ⊕ Redis SETNX     │ │     │       │ ⊕ projection     │
                                  │ ⊕ balance fast-fail│ │     │       │                  │
                                  │ ⊕ Hikari pool 25  │ │     │       │ GET /analytics/  │
                                  └───────────────────┘ │     │       │     volume       │
                                                        │     │       └──────────────────┘
                                                        │     │
                                                        ▼     │
                                            ┌──────────────────────────┐
                                            │ ledger-service (8082)    │
                                            │                          │
                                            │ ⊕ inbox dedup            │
                                            │ ⊕ @Transactional debit + credit  │
                                            │ ⊕ outbox relay (completed/failed) │
                                            │ ⊕ Redis balance cache    │
                                            │ ⊕ AFTER_COMMIT eviction  │
                                            │ ⊕ @RetryableTopic + DLT  │
                                            │ ⊕ listener concurrency=3 │
                                            └──────────────────────────┘
```

### Flow

1. **Client → gateway.** `POST /v1/payments` validates input, claims `transaction_id` in Redis (`SET NX EX 24h`) for cross-replica idempotency, **fast-fails on insufficient funds** by reading the ledger's shared Redis balance cache (`BalanceCacheReader` — fail-open on miss), then writes the `transactions` row **and** the matching `outbox_events` row in one DB transaction, returns **202 Accepted**.
2. **Gateway outbox → Kafka.** A `@Scheduled` relay (every 100 ms, batch 200) drains `outbox_events` via `SELECT … FOR UPDATE SKIP LOCKED`, sends to Kafka, flips status `PENDING → PUBLISHING → PUBLISHED`. Backed by a rescue sweeper that resets rows stuck in `PUBLISHING`.
3. **Ledger consumer.** `@KafkaListener` with `@RetryableTopic` for transient errors; one inbox row claimed per event so a redelivery never re-debits.
4. **Ledger settles.** Inside one `@Transactional`: debit sender, credit receiver (optimistic-lock on `accounts.version`), write a `payment-completed`/`payment-failed` row to **its** outbox.
5. **AFTER_COMMIT cache invalidation.** `BalanceCacheInvalidator` fires on `TransactionalEventListener(AFTER_COMMIT)` — Redis eviction only happens once the DB commit is durable; rollbacks don't wipe the cache.
6. **Ledger outbox → Kafka → analytics.** Identical relay pattern. Analytics-worker dedups via its own inbox table and projects to `swiftpay_analytics.analytics_transactions`.

---

## Load test — measured

SwiftPay is benchmarked with **k6** against `POST /v1/payments` and verified end-to-end through Kafka, Postgres, and Redis. **The system sustained 250 TPS in steady state**, validated both by k6's own per-second rate reporting and by an independent Postgres / Kafka-lag sampler running alongside the test. A **PCAP capture** during the run covers every component port and provides network-level evidence.

### Measured results

| Metric | Value |
|---|---|
| Tool / executor | k6, `ramping-arrival-rate` |
| Workload | `POST /v1/payments` with fresh `transaction_id` per request |
| Target rate | **250 TPS sustained** |
| **Achieved sustained rate** | ✅ **250 iters/s** (k6 stage progress: `sustained_250_tps  250.00 iters/s`) — also confirmed by live DB sampler peaking at **256 TPS** during the sustained phase |
| Full-scenario average rate | 193 iters/s (includes 30 s warm-up + ramps + ramp-down, where target rates are lower by design) |
| **HTTP failures** | **0 / 57 899** |
| **Acceptance rate** | **100 %** (every request → 202) |
| Latency p(50) / p(95) / p(99) | **1.89 ms / 4.34 ms / 8.42 ms** |
| Latency max | 159 ms (single outlier; warm-up JIT/GC) |
| Total successful payments (5-min run) | **57 899** |
| Event loss / DLT / poison records | **0** |
| Outbox PENDING after run | drained to 0 |

> The "sustained 250 TPS" claim refers to the 3-minute steady-state stage of the scenario, where k6 holds a constant arrival rate of 250 iters/s. The 193 iters/s headline number some k6 tools quote is the *time-weighted average across all stages* — it includes the warm-up and ramp phases that intentionally run slower. During the sustained stage itself, the system held 250 iters/s with zero failures.

### Network evidence — PCAP capture

A real `tcpdump` capture on the loopback interface during a 250 TPS run, filtered to SwiftPay's six ports, proves every component participated in the test:

| Port | Component | Packets captured |
|---|---|---|
| 8081 | transaction-gateway HTTP | **223 128** |
| 29092 | Apache Kafka (external listener) | **550 968** |
| 5432 | PostgreSQL | **4 061 530** |
| 6379 | Redis | **375 263** |
| 8082 / 8083 | ledger / analytics HTTP | event-driven, no client-side traffic |
| **Total** | | **5 210 889** |

Capture lives at `loadtest/pcap/swiftpay-load-*.pcap.gz` (156 MB gzip / 814 MB raw). Full per-port breakdown + analysis tips in **`loadtest/DRYRUN_REPORT.md`**.

### Reports backing the 250 TPS claim

| File | What it proves |
|---|---|
| `loadtest/reports/summary.json`  | k6 raw JSON: 57 899 iterations, 0 failures, p(99) = 8.42 ms |
| `loadtest/reports/summary.html`  | k6 human-readable HTML summary (open in a browser) |
| `loadtest/pcap/swiftpay-load-*.pcap.gz` | tcpdump capture of every HTTP / Kafka / Postgres / Redis packet during the run |
| `loadtest/DRYRUN_REPORT.md`      | Per-port packet breakdown + k6 stage observations + DB state at end of run |
| `loadtest/BOTTLENECK_ANALYSIS.md` | Live 15-second-interval samples from Postgres + Kafka during the run — independent confirmation of the 250 TPS sustained rate and zero consumer lag |

### Run the tests yourself

```bash
brew install k6                              # one-time
docker compose up -d --build                 # bring up the stack

# Quick 5-min load test (no PCAP)
k6 run loadtest/payments.js
open loadtest/reports/summary.html

# Load test + PCAP capture (sudo prompt once for BPF)
loadtest/run-with-pcap.sh quick              # 5-min run with full network capture
```

The wrapper script captures **HTTP (8081/8082/8083)**, **Kafka (29092)**, **PostgreSQL (5432)**, and **Redis (6379)** traffic on `lo0`, runs k6 alongside, and stops `tcpdump` cleanly. Output:

```
loadtest/reports/summary.html              ← k6 HTML
loadtest/reports/summary.json              ← k6 JSON
loadtest/pcap/swiftpay-load-<timestamp>.pcap   ← raw network capture
```

See `loadtest/README.md` for the full procedure (including the optional 1 M-transaction long-run script) and PCAP analysis tips.

---

## Tech stack

- **Java 25** (Temurin), **Spring Boot 3.5.0**
- **Gradle 9** — one wrapper per service, configuration cache enabled
- **PostgreSQL 14** — three separate databases (`swiftpay_gateway`, `swiftpay_ledger`, `swiftpay_analytics`)
- **Apache Kafka 3.8** in **KRaft mode** (no Zookeeper)
- **Redis 7** — idempotency (gateway) + balance cache (ledger)
- **Flyway 10.20.1** for migrations
- **Springdoc OpenAPI 2.8.0** — Swagger UI on gateway and ledger
- **Lombok**, hand-written mappers (no MapStruct)
- **JUnit 5, Mockito, Testcontainers, AssertJ, Awaitility, JaCoCo 0.8.13**
- **k6** for load testing

---

## Repository layout

```
SwiftPay/
├── transaction-gateway/    HTTP edge (port 8081). Own Gradle root + Dockerfile + CI workflow.
├── ledger-service/         Financial source of truth (port 8082). Own Gradle root.
├── analytics-worker/       Read-side projection (port 8083). Own Gradle root.
├── docker-compose.yml      Kafka KRaft + Kafka UI + the three services (each built from its own folder)
├── loadtest/               k6 load test scripts + PCAP capture orchestration
├── k8s/                    Optional Kustomize manifests (base + overlays/local)
└── .github/workflows/      Per-service CI (path-scoped, Gradle + Docker cache)
```

No root Gradle build and no cross-copying in any Dockerfile — each service is independently buildable and deployable.

---

## Quick start

### Prerequisites
- Docker Desktop
- (Optional, for native runs) JDK 25 auto-provisions via the Gradle toolchain — you just need Gradle to be able to fetch it.
- PostgreSQL + Redis on the host (or override the env vars to point at containers).

### Full stack via Docker

```bash
docker compose up -d --build
for p in 8081 8082 8083; do curl -fsS "http://localhost:$p/actuator/health" && echo; done
```

### A single service from source

```bash
cd transaction-gateway && ./gradlew bootRun     # 8081
cd ledger-service     && ./gradlew bootRun      # 8082
cd analytics-worker   && ./gradlew bootRun      # 8083
```

### Submit a payment

```bash
curl -X POST http://localhost:8081/v1/payments \
  -H 'Content-Type: application/json' \
  -d "$(cat <<EOF
{
  "sender_id": 1,
  "receiver_id": 2,
  "amount": 25.00,
  "currency": "USD",
  "transaction_id": "$(uuidgen | tr A-Z a-z)"
}
EOF
)"
```

### Useful endpoints

| URL                                          | What you'll see                       |
|----------------------------------------------|----------------------------------------|
| http://localhost:8081/swagger-ui/index.html  | Gateway API docs                       |
| http://localhost:8082/swagger-ui/index.html  | Ledger API docs                        |
| http://localhost:8083/analytics/volume       | Aggregate volume                       |
| http://localhost:8080                        | Kafka UI                               |
| http://localhost:8081/actuator/health        | Gateway health                         |
| http://localhost:8082/actuator/health        | Ledger health                          |
| http://localhost:8083/actuator/health        | Analytics health                       |

---

## Kubernetes — optional deployment

SwiftPay also ships **Kustomize-friendly Kubernetes manifests** for deployment to a local cluster (Docker Desktop K8s, Minikube, kind). This is **additive** — it does **not** replace Docker Compose, native `bootRun`, or the GitHub Actions CI workflows.

| Mode | Required? | Command |
|---|---|---|
| Local Gradle (`./gradlew bootRun`) | unchanged | works as documented above |
| Docker Compose                     | unchanged | `docker compose up -d --build` |
| GitHub Actions CI                  | unchanged | runs on every push, no cluster needed |
| **Kubernetes (new, optional)**     | only if you want it | `kubectl apply -k k8s/overlays/local` |

### Manifest layout (Kustomize)

```
k8s/
├── base/                          # environment-agnostic manifests
│   ├── namespace.yaml             # swiftpay namespace
│   ├── postgres-init-configmap.yaml  # creates swiftpay_gateway / swiftpay_ledger / swiftpay_analytics
│   ├── postgres.yaml              # StatefulSet (+ PVC) + headless Service
│   ├── redis.yaml                 # Deployment + Service + ACL config
│   ├── kafka.yaml                 # StatefulSet (KRaft single-node) + Service + topic-init Job
│   ├── transaction-gateway.yaml   # Deployment + ClusterIP Service
│   ├── ledger-service.yaml        # Deployment + ClusterIP Service
│   ├── analytics-worker.yaml      # Deployment + ClusterIP Service
│   └── kustomization.yaml
└── overlays/
    └── local/                     # patches for local clusters
        ├── kustomization.yaml
        ├── patch-image-pull-policy.yaml      # imagePullPolicy: Never
        └── patch-service-nodeport-*.yaml     # NodePort 30081 / 30082 / 30083
```

### Deploy to a local cluster

```bash
# 1. Build the three images (Docker Desktop K8s reuses local images automatically;
#    for Minikube, run `minikube image load swiftpay/<service>:0.0.1-SNAPSHOT` after each)
docker build -t swiftpay/transaction-gateway:0.0.1-SNAPSHOT ./transaction-gateway
docker build -t swiftpay/ledger-service:0.0.1-SNAPSHOT     ./ledger-service
docker build -t swiftpay/analytics-worker:0.0.1-SNAPSHOT   ./analytics-worker

# 2. Apply the local overlay
kubectl apply -k k8s/overlays/local

# 3. Watch pods come up
kubectl -n swiftpay get pods -w

# 4. Hit the services on NodePorts
curl -fsS http://localhost:30081/actuator/health
curl -fsS http://localhost:30082/actuator/health
curl -fsS http://localhost:30083/actuator/health
```

### Tear down

```bash
kubectl delete -k k8s/overlays/local
```

### Render manifests offline (sanity check, no cluster needed)

```bash
kubectl kustomize k8s/overlays/local | less
```

Renders 16 objects: namespace, 2 ConfigMaps, 6 Services, 4 Deployments, 2 StatefulSets, 1 Job — all in the `swiftpay` namespace.

---

## Continuous integration

`.github/workflows/<service>.yml` per service:

- **Triggers:** push, pull_request, workflow_dispatch — all with `paths:` filters scoped to the service folder.
- **Steps:** checkout → setup-java 25 → setup-gradle (caching) → `assemble` → `test jacocoTestReport` → upload JUnit / JaCoCo HTML & XML / boot JAR → buildx Docker image (no push, layer-cached via GHA).
- **Caching:** Gradle home (deps + wrapper distributions + config cache) via `gradle/actions/setup-gradle@v4`; Docker layers via `cache-from/to: type=gha,scope=<service>`.
- **Cache safety:** `cache-read-only: ${{ github.ref != default-branch }}` — PRs can read but not write the cache, so a malicious PR can't poison the cache.
- **Concurrency:** in-flight CI is cancelled when a new commit lands on the same branch / PR.

