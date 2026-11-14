# Tech radar

Shared, dated knowledge for the agents: what's current, what's risky, what to adopt. Refreshed daily; merged only after human review.

**Last checked:** 2026-11-14

## Action needed (security)

- CVE-2026-47834 — Spring Data JPA 4.0.0–4.0.6 (Boot 4.0.7 ships 4.0.6) sort-expression validation bypass, Medium — fixed in Spring Data JPA 4.0.7 (Boot 4.0.8+); we bump to Boot 4.0.9 — [advisory](https://spring.io/security/cve-2026-47834) (2026-08-20), [Boot 4.0.8](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.8).
- Oracle Java SE CPU (Oct 2026): JDK 25.0.5 fixes 55 vulns; 25.0.5.0.3 has a further security alert fix — use current JDK 25 images (`eclipse-temurin:25`, graalvm `:25`) and rebuild — [Oracle 25.0.5.0.3 notes](https://www.oracle.com/uk/java/technologies/javase/25-0-5-0-3-relnotes.html) (2026-10-24).
- PostgreSQL 2026-11-12 update (18.7, 17.12, …) — we use `postgres:17-alpine` (floating tag): re-pull to get 17.12; CVE list not verified — [postgresql.org](https://www.postgresql.org/support/security/) (2026-11-12).

## Stack baseline

| Tech | We use | Latest stable | Note |
|------|--------|---------------|------|
| Java | 25 | 25.0.5 (CPU Oct 2026) | [Oracle](https://www.oracle.com/uk/java/technologies/javase/25-0-5-0-3-relnotes.html) |
| Spring Boot (parent) | 4.0.9 | 4.0.9 / 4.1.2 | 4.1 is a minor upgrade, not done; [releases](https://github.com/spring-projects/spring-boot/releases) (2026-10-22) |
| springdoc-openapi | 3.0.3 | 3.2.0 | Minor upgrade; dependabot has 3.1.1 PR open. |
| Flyway | managed by Boot (11.14.1) | 13.12.2 | Upgrade with Boot, not alone. |
| PostgreSQL JDBC | managed (42.7.14 in Boot 4.0.9) | 42.7.14 | OK after bump. |
| Micrometer / tracing | managed | 1.17.2 / 1.7.2 | Boot 4.0.9 ships 1.16.8 / 1.6.8. |
| OpenTelemetry OTLP exporter | 1.55.0 | 1.68.0 | Minor; pinned in pom. |
| Mockito | 5.20.0 | 5.24.0 | Minor. |
| Postgres image | 17-alpine | 17.12 | See Action needed. |

Source for "latest": versions-maven-plugin report, 2026-11-14.

## Adopt (new features worth using)

_None recorded yet._

## Avoid / deprecated

- PostgreSQL 14 reaches end of life 2026-11-12 — do not target it — [postgresql.org](https://www.postgresql.org/support/security/).

## Changelog

- 2026-11-14 — Boot 4.0.7→4.0.9 for CVE-2026-47834; added Java CPU and Postgres notes. Not checked (sources unreachable/time): springdoc, Flyway, Testcontainers, GraalVM, OTel advisories, GitHub Actions, Claude Code changelog.
