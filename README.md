# service-archetype

A best-practice **Spring Boot 4** REST microservice archetype, built on **Java 25** and
packaged as a **GraalVM native image**, with **Flyway** migrations, **Testcontainers**
integration tests, and a set of **SDLC subagents** for Claude Code.

Use it as the reference template when starting a new service. Don't copy by hand — run the
generator. It prompts for the project name and groupId interactively:

```bash
scripts/new-project.sh
# Project name (artifactId): order-service
# Maven groupId: com.acme
# Base package [com.acme.orderservice]:
# Output directory [../order-service]:
# Proceed? [Y/n]:
```

…or pass everything as flags for scripting/CI:

```bash
scripts/new-project.sh --name order-service --group com.acme --package com.acme.orderservice
```

Prefer the native Maven command? There's also an installable **Maven archetype**:

```bash
mvn archetype:generate \
  -DarchetypeGroupId=com.anbit \
  -DarchetypeArtifactId=service-archetype \
  -DarchetypeVersion=0.1.0
```

(Build/install it first with `./archetype/sync.sh && mvn -f archetype/pom.xml install`, or
publish it to your registry — see [`archetype/README.md`](archetype/README.md).)

Either way it stamps out a new, self-contained project with the base package, Maven
coordinates, and app name renamed throughout (sources, tests, config, k6, observability,
docs) — then `cd` in and `./mvnw verify`.

**New here? See [`GETTING_STARTED.md`](GETTING_STARTED.md)** — a short dev guideline for
generating, configuring, and running a project locally. The full reference (architecture,
APIs, observability, environments, packaging) stays in this README. Generator details are
in [`scripts/README.md`](scripts/README.md).

> Verified on this machine: `./mvnw verify` is green — 4 unit tests + 11 integration tests
> running against a real Postgres (Testcontainers 2.0.2) with real Flyway migrations, on
> Spring Boot 4.0.0 / Spring Framework 7.0.1 / Java 25.0.2. The integration tests cover the
> synchronous order API, the asynchronous fulfillment (202 + job polling), the centralised
> exception handling, the OpenAPI spec/Swagger UI, and the observability endpoints.

## Stack

| Concern        | Choice                                                        |
|----------------|--------------------------------------------------------------|
| Language/JDK   | Java 25 (virtual threads, AOT)                               |
| Framework      | Spring Boot 4.0.0 / Spring Framework 7 (Jackson 3, JSpecify) |
| Web server     | Embedded **Jetty** (Tomcat excluded)                         |
| Build          | Maven (wrapper included)                                     |
| Persistence    | Spring Data JPA + PostgreSQL, schema owned by Flyway 11      |
| Packaging      | GraalVM native image (distroless) + JVM image fallback       |
| API docs       | OpenAPI 3 + Swagger UI (springdoc 3.0.3)                     |
| Testing        | JUnit 5, Mockito, AssertJ, Testcontainers, `RestTestClient`  |
| Performance    | k6 scenarios (smoke/load/stress/async) with thresholds       |
| Observability  | Micrometer + Prometheus metrics, OTLP tracing, structured logs|
| Logging        | **Log4j2** (Logback excluded), driven by Spring Boot `logging.*` |
| Ops            | Actuator (health/metrics/prometheus), graceful shutdown      |

## Project layout (package-by-layer)

```
src/main/java/com/anbit/archetype/
├── Application.java
├── package-info.java          # @NullMarked (JSpecify) for the whole tree
├── controller/                # HTTP boundary: thin, validate + delegate + map DTOs
├── service/                   # business logic + @Transactional (incl. FulfillmentService @Async)
├── repository/                # Spring Data persistence boundary (@EntityGraph finders)
├── domain/                    # JPA entities + enums (Product, Order, OrderItem, ProcessingJob, …)
├── dto/                       # request/response records (the wire contract)
├── common/error/              # ProblemDetail (RFC 9457) global handling
└── config/                    # @Configuration: Clock, AsyncConfig, OpenApiConfig
src/main/resources/
├── application.yml            # + application-{dev,uat,staging,prod}.yml
└── db/migration/              # V1__init.sql, V2__catalog_and_orders.sql (Flyway owns schema)
src/test/java/.../             # tests mirror the layer: service/*Test (unit), controller/*IT (Testcontainers)
```

The conventions (thin controllers, service-owned transactions, DTOs at the boundary,
Flyway-owned schema, `@NullMarked`, injected `Clock`, native-image safety) are documented
inline in `package-info.java` files and enforced by the SDLC agents. The full layering and
naming rules — controller / service / repository / domain / dto / error / config — are in
**[`ARCHITECTURE.md`](ARCHITECTURE.md)**.

## Domain model & relations

```
Category 1 ──< * Product           Product.category    @ManyToOne (LAZY, nullable)
Order    1 ──< * OrderItem          Order.items         @OneToMany (cascade ALL, orphanRemoval)
Product  1 ──< * OrderItem          OrderItem.product   @ManyToOne (LAZY), unit price snapshotted
```

`Order` is the aggregate root: it owns its items and derives `totalAmount` from them — the
client never sets the total. Reads that cross a relation use `@EntityGraph` finders (e.g.
`findWithItemsById`) so responses map cleanly with `open-in-view` disabled.

## Sync vs. async processing

**Synchronous** — work happens in-request; the response is the finished result.

```bash
# Create a product and an order (order total is computed server-side)
PID=$(curl -s -X POST localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Widget","price":12.50}' | jq -r .id)

curl -s -X POST localhost:8080/api/v1/orders \
  -H 'Content-Type: application/json' \
  -d "{\"customerName\":\"Grace\",\"items\":[{\"productId\":\"$PID\",\"quantity\":3}]}"
# → 201 Created, body includes status=CREATED and totalAmount=37.50
```

**Asynchronous** — the request is *accepted*, work runs in the background on a virtual-thread
executor, and the client polls a job for the outcome.

```bash
OID=...   # an existing order id

# Kick off fulfillment — returns immediately
curl -si -X POST localhost:8080/api/v1/orders/$OID/fulfillment
# → 202 Accepted
#   Location: /api/v1/jobs/{jobId}
#   body: { "status": "PENDING", ... }

# Poll the job until COMPLETED (or FAILED)
curl -s localhost:8080/api/v1/jobs/{jobId}
# → { "status": "RUNNING" }  →  { "status": "COMPLETED", "result": "Order ... fulfilled" }
```

This is the standard *request → 202 + acknowledge → poll* pattern for long-running work;
`ProcessingJob` makes the result durable and observable across requests and instances.

## Quick start

Requires **JDK 25**, **Docker** (for integration tests / local Postgres).

```bash
# 1. Start Postgres for local runs
docker compose up -d

# 2. Run unit tests (fast, no Docker)
./mvnw test

# 3. Full build incl. Testcontainers integration tests (needs Docker)
./mvnw verify

# 4. Run the app
./mvnw spring-boot:run            # add: -Dspring-boot.run.profiles=dev
# → http://localhost:8080/api/v1/products   (Actuator: /actuator/health)
```

If Maven reports `release version 25 not supported`, your `mvnw` is using an older JDK —
point `JAVA_HOME` at a JDK 25 install.

### Try the API

```bash
curl -s -X POST localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' \
  -d '{"name":"Widget","description":"A useful widget","price":19.99}'

curl -s localhost:8080/api/v1/products
```

## GraalVM native image

```bash
# Local (needs GraalVM for JDK 25; native-image 25+ is mandatory for Boot 4)
./mvnw -Pnative native:compile      # first build is slow: AOT analysis ~2–5 min

# Container images
docker build -t service-archetype:native .          # native, distroless, sub-100ms start
docker build -f Dockerfile.jvm -t service-archetype:jvm .   # JVM, faster to build
```

## API documentation (OpenAPI / Swagger)

springdoc generates an OpenAPI 3 spec from the controllers, DTO records, and validation
constraints — no hand-written spec to maintain.

| What        | URL                          |
|-------------|------------------------------|
| Swagger UI  | `/swagger-ui.html`           |
| OpenAPI JSON| `/v3/api-docs`               |
| OpenAPI YAML| `/v3/api-docs.yaml`          |

Endpoints are grouped by `@Tag` per controller; `ProductController` shows how to enrich
operations with `@Operation`/`@ApiResponse`. Scanning is limited to the app's base package
(`springdoc.packages-to-scan`). In **prod** the interactive UI is disabled
(`springdoc.swagger-ui.enabled=false`) while the machine-readable spec stays available for
gateways/portals.

## Database migrations

Flyway owns the schema; Hibernate runs with `ddl-auto: validate`. Every change is a **new**
forward-only file `src/main/resources/db/migration/V<n>__<description>.sql`. Never edit a
migration that has already been applied to a shared environment.

## Database & connection pool config

The datasource and **HikariCP** pool are configured in `application.yml`, env-var driven so
the same image runs in every environment:

| Setting                | Env var                      | Default | Notes                                  |
|------------------------|------------------------------|---------|----------------------------------------|
| JDBC URL               | `DB_URL`                     | local pg| `jdbc:postgresql://host:5432/db`       |
| Username / password    | `DB_USERNAME` / `DB_PASSWORD`| `app`   | inject via secrets in deployed envs    |
| Max pool size          | `DB_POOL_MAX_SIZE`           | 10      | 15 (uat) / 20 (staging) / 30 (prod)    |
| Min idle               | `DB_POOL_MIN_IDLE`           | 2       | raised per env                         |
| Connection timeout (ms)| `DB_POOL_CONNECTION_TIMEOUT_MS` | 30000 | wait time for a free connection      |
| Max lifetime (ms)      | `DB_POOL_MAX_LIFETIME_MS`    | 1800000 | keep below the DB/LB idle cutoff       |

`auto-commit` is disabled (Spring manages transactions), Hibernate uses UTC and JDBC
batching (`batch_size=25`, ordered inserts/updates). Migrations are validated on startup.

## Configuration & environments

Base config lives in `application.yml`; each environment overrides it in its own profile,
selected with `SPRING_PROFILES_ACTIVE` (e.g. `SPRING_PROFILES_ACTIVE=prod`):

| Profile   | Logs            | App log level | Trace sampling | Actuator exposure          | Pool |
|-----------|-----------------|---------------|----------------|----------------------------|------|
| `dev`     | human-readable  | DEBUG         | 1.0            | everything, details always | 10   |
| `uat`     | JSON (ECS)      | DEBUG         | 0.5            | + `loggers`                | 15   |
| `staging` | JSON (ECS)      | INFO          | 0.2            | health/info/metrics/prom   | 20   |
| `prod`    | JSON (ECS)      | INFO (root WARN)| 0.1          | health/info/prom, no details| 30   |

```bash
SPRING_PROFILES_ACTIVE=staging \
DB_URL=jdbc:postgresql://db:5432/app DB_USERNAME=... DB_PASSWORD=... \
OTLP_TRACING_ENDPOINT=http://otel-collector:4318/v1/traces \
java -jar app.jar
```

Secrets are never baked into the image — supply them via env or the mounted config tree
(`spring.config.import=configtree:/run/secrets/`).

## Logging & observability

- **Metrics** — Micrometer + Prometheus at `/actuator/prometheus`. Tagged with
  `application`; `http.server.requests` ships a latency histogram + SLO buckets. A sample
  **custom business metric** (`orders_placed_total`) shows the pattern in `OrderService`.
- **Tracing** — Micrometer Tracing over OpenTelemetry, exported via **OTLP** to a collector
  (`OTLP_TRACING_ENDPOINT`). Sampling is 0 locally/in tests and raised per environment.
- **Logs** — backend is **Log4j2** (`spring-boot-starter-log4j2`; Logback excluded), driven
  by Spring Boot's `logging.*` properties — human-readable in dev, **structured JSON (ECS)**
  in deployed profiles. When a request is sampled, `traceId`/`spanId` are injected into every
  log line for correlation. No `log4j2.xml` is required; add a `log4j2-spring.xml` only for
  custom appenders (it then takes over from the `logging.structured.format` integration).
- **Health** — `/actuator/health` with liveness/readiness probes (readiness includes the DB).

Spin up a local stack (Prometheus + Tempo + OTel Collector + Grafana) and run the app in
`dev` to see metrics and traces end-to-end:

```bash
docker compose -f observability/compose-observability.yaml up -d
SPRING_PROFILES_ACTIVE=dev ./mvnw spring-boot:run
# Grafana → http://localhost:3000   Prometheus → http://localhost:9090
```

See [`observability/`](observability/) for the stack and configs.

## Performance testing (k6)

[`k6/`](k6/) holds load-test scenarios with built-in thresholds (so they gate CI):
`smoke.js`, `load.js`, `stress.js`, and `async-fulfillment.js` (the 202 + poll flow, with a
custom end-to-end latency trend). Run against any environment:

```bash
k6 run k6/smoke.js
k6 run -e BASE_URL=https://staging.example.com k6/load.js
```

Thresholds mirror the SLO buckets in `application.yml`, so k6 and Prometheus agree on the
latency budget. See [`k6/README.md`](k6/README.md).

## Continuous integration

GitHub Actions workflows live in [`.github/workflows/`](.github/workflows):

| Workflow         | Trigger                       | Does                                                            |
|------------------|-------------------------------|----------------------------------------------------------------|
| `ci.yml`         | push to `main`, pull requests | JDK 25, `./mvnw verify` (unit + Testcontainers integration), caches `~/.m2`, uploads test reports + jar. The merge gate. |
| `native.yml`     | `workflow_dispatch`, `v*` tags | GraalVM 25 native build, uploads the executable.               |
| `perf-smoke.yml` | `workflow_dispatch`           | Boots the app against a throwaway Postgres and runs `k6 smoke` (fails on SLO breach). |

Notes:
- Testcontainers needs no special config on GitHub's `ubuntu-latest` runners — Docker is
  preinstalled and the daemon socket is at the default path.
- `ci.yml` is the only one that runs on every change; native and perf are kept off the PR
  path because they're slow. Promote `perf-smoke` to `pull_request` once its thresholds are
  tuned to your infra.
- After pushing to GitHub, add a status badge:
  `![CI](https://github.com/<owner>/<repo>/actions/workflows/ci.yml/badge.svg)`.

## Dependency security

- **Stay current.** The single most effective control is keeping the Spring Boot parent on
  the latest 4.0.x patch — it cascades security fixes to Spring Framework, the embedded
  server, Jackson, the JDBC driver, Hibernate, etc. (e.g. 4.0.7 carries Framework 7.0.8,
  Tomcat-EL 11.0.22, Jetty 12.1.10, Postgres 42.7.11 and fixes CVE-2026-40976 / -41842).
- **Automated, server-side scanning** — [`.github/dependabot.yml`](.github/dependabot.yml)
  raises weekly version-update PRs (grouped) and immediate security-update PRs from the
  GitHub Advisory DB. Turn on *Dependabot alerts + security updates* in the repo's
  Settings → Code security.
- **On-demand deep scan** — an opt-in OWASP Dependency-Check (NVD) gate:
  ```bash
  NVD_API_KEY=... ./mvnw -Psecurity verify   # fails on any dependency with CVSS >= 7
  ```
  (Free key: <https://nvd.nist.gov/developers/request-an-api-key>. First run caches the NVD.)
- **Known gap — no authentication/authorization.** This archetype ships no Spring Security,
  so the REST endpoints are open by default. Add `spring-boot-starter-security` (pinned to a
  patched version) and a filter chain before exposing the service; until then, gate it at the
  network/gateway layer. (Actuator already restricts detail to `when-authorized`.)

## SDLC multi-agent setup

`.claude/agents/` contains one Claude Code subagent per SDLC phase — requirements → design
→ implementation → testing → review (+ security) → build/release — plus Scrum roles
(product owner, scrum master, UX designer). Each is a scoped specialist that knows this
archetype's conventions and hands off to the next. See
[`.claude/agents/README.md`](.claude/agents/README.md) for the pipeline and how to drive it.

The process itself (roles, Scrum ceremonies, phase gates, definition of done) is defined
machine-readably in [`sdlc.yaml`](sdlc.yaml). The backlog lives in `PRD.md`
(template: [`docs/templates/PRD_TEMPLATE.md`](docs/templates/PRD_TEMPLATE.md)); running
`/prd-to-jira` parses it into linked Epics/Stories/Subtasks and creates or syncs them in
Jira via [`.claude/skills/jira/SKILL.md`](.claude/skills/jira/SKILL.md) (Atlassian MCP
preferred, REST API token fallback — see `.env.example`).

## Notes & known caveats

- **OpenAPI** uses `springdoc-openapi` 3.0.3, which is Jackson-3 / Spring Boot 4 compatible
  (earlier 3.0.x pulled Jackson 2 — fixed). See the *API documentation* section.
- **Log4j2, not Logback.** Logback is removed with a single exclusion on the core
  `spring-boot-starter` (every starter reaches Logback only through it), then
  `spring-boot-starter-log4j2` provides the binding. Keep them in sync: if you ever add a
  starter that bundles its own logging, re-check `dependency:tree` for a stray `logback`.
- **Boot 4 modularization gotchas baked into this pom:** Flyway autoconfiguration now lives
  in `spring-boot-flyway` (added explicitly), and Testcontainers 2.x renamed its modules to
  `testcontainers-postgresql` / `testcontainers-junit-jupiter` (Boot 4 manages v2.0.2).
- **`TestRestTemplate`/`WebTestClient` (MVC) were removed/relocated in Boot 4** — use
  Spring Framework 7's `RestTestClient` (see `ProductControllerIT`).
- **Mockito** runs as a `-javaagent` (configured in `pom.xml`) because JVM self-attach is
  deprecated on modern JDKs.
- **Prometheus metric naming:** avoid counter names ending in `created` — the Prometheus
  client reserves the `_created` suffix, so `orders.created` would export as `orders_total`.
  The sample metric is named `orders.placed` → `orders_placed_total`.
- **Testcontainers + Docker:** if you see "Could not find a valid Docker environment", set
  `DOCKER_HOST` to your active socket; a daemon HTTP 400 means the Testcontainers version
  lags your Docker engine — bump it.
