# Getting started (dev)

Generate a project from the archetype, configure it for local development, and run it.
For architecture, the relation model, observability, environments, k6, and packaging, see
[`README.md`](README.md). Contributing to *this* archetype repo itself, rather than a
project generated from it? See [`CONTRIBUTING.md`](CONTRIBUTING.md) for the git workflow.

## 1. Prerequisites

- **JDK 25** (`java -version`) — Maven runs via the bundled `./mvnw`.
- **Docker** (`docker info`) — for local Postgres and integration tests.

> If you hit `release version 25 not supported`, point the wrapper at JDK 25:
> `export JAVA_HOME=$(/usr/libexec/java_home -v 25)` (macOS) or your distro's path.

## 2. Generate the project

From the archetype repo (prompts for name + groupId):

```bash
scripts/new-project.sh
# Project name (artifactId): order-service
# Maven groupId: com.acme
# Base package [com.acme.orderservice]:     ← Enter for default
# Output directory [../order-service]:      ← Enter for default
# Proceed? [Y/n]:
```

Or non-interactively:

```bash
scripts/new-project.sh --name order-service --group com.acme --package com.acme.orderservice
```

Then work inside the generated project:

```bash
cd ../order-service
```

## 3. Configure for dev

Local defaults are already wired in `application.yml` and the `dev` profile
(`application-dev.yml`) — no changes needed to run locally. Defaults:

| Setting        | Dev default                                   | Override (env var) |
|----------------|-----------------------------------------------|--------------------|
| DB URL         | `jdbc:postgresql://localhost:5432/app`        | `DB_URL`           |
| DB user / pass | `app` / `app`                                 | `DB_USERNAME` / `DB_PASSWORD` |
| Server port    | `8080`                                        | `SERVER_PORT`      |
| Logs           | human-readable, app at `DEBUG`                | —                  |
| Tracing        | sampled 100%, OTLP → `localhost:4318`         | `OTLP_TRACING_ENDPOINT` |

The `dev` profile also exposes all actuator endpoints and echoes SQL. These defaults match
`compose.yaml`, so the next step works with no extra setup.

## 4. Run

```bash
docker compose up -d                                   # Postgres on :5432
./mvnw spring-boot:run -Dspring-boot.run.profiles=dev  # service on :8080
```

Verify it's up:

```bash
curl -s localhost:8080/actuator/health      # {"status":"UP",...}
```

Browse the API in **Swagger UI → http://localhost:8080/swagger-ui.html** (spec at
`/v3/api-docs`).

Quick smoke of the API:

```bash
curl -s -X POST localhost:8080/api/v1/products \
  -H 'Content-Type: application/json' -d '{"name":"Widget","price":12.50}'
```

Stop dependencies when done: `docker compose down` (add `-v` to wipe data).

## 5. Test

```bash
./mvnw test      # unit tests, no Docker
./mvnw verify    # + Testcontainers integration tests (needs Docker)
```

---

Everything else — project structure, sync/async APIs, the relation model, observability
stack, `uat`/`staging`/`prod` profiles, performance testing, native/Docker packaging, and
troubleshooting — is documented in [`README.md`](README.md).
