# Tech radar

Shared, dated knowledge for the agents: what's current, what's risky, what to adopt. Refreshed daily; merged only after human review.

**Last checked:** 2026-11-22

## Action needed (security)

- CVE-2026-47834 — Spring Data JPA 4.0.0–4.0.6 (Boot 4.0.7 ships 4.0.6) sort-expression validation bypass, Medium — fixed in Spring Data JPA 4.0.7 (Boot 4.0.8+); we bump to Boot 4.0.9 — [advisory](https://spring.io/security/cve-2026-47834) (2026-08-20), [Boot 4.0.8](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.8).
- Spring Security CVE-2026-59273 (Critical, SpEL RCE, 7.0.0–7.0.8; related High/Low CVEs share the fix) — fixed in 7.0.9 (Boot 4.0.10); not affected, no Spring Security dependency in the archetype or in any service pom — [advisory](https://spring.io/security/cve-2026-59273), [Boot 4.0.10](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.10) (2026-11-19).
- CVE-2026-114938 "Java4Shell" — Base64 intrinsic memory corruption, RCE on x86-64 with AVX-512 VBMI (ARM64 not affected), CVSS 8.1; JDK 25 ≤ 25.0.5.0.3 affected, fixed in 25.0.5.1 — JVM images pinned to `eclipse-temurin:25.0.5.1_2` (ADR 0003); native images stay on GraalVM `:25` until a 25.0.5.1 build exists; interim flag `-XX:-UseBASE64Intrinsics` — [Tenable FAQ](https://www.tenable.com/blog/java4shell-cve-2026-114938-frequently-asked-questions-about-openjdk-rce-vulnerability), [RHSA-2026:100573](https://access.redhat.com/errata/RHSA-2026:100573) (2026-11-18).
- CVE-2026-114938 also affects the JDK 17 and 21 lines (fixed in 17.0.21.1 and 21.0.13.1): any image on those lines needs its own fixed build. Accepted local-dev gaps in services: Kafka Connect `apache/kafka:3.7.0` (Temurin 21, search-service compose) and Flink `apache/flink:1.20.1-…-java17` (ingestion-service compose) — [Tenable FAQ](https://www.tenable.com/blog/java4shell-cve-2026-114938-frequently-asked-questions-about-openjdk-rce-vulnerability).
- Oracle Java SE CPU (Oct 2026): JDK 25.0.5 fixes 55 vulns; 25.0.5.0.3 has a further security alert fix — superseded by 25.0.5.1 above — [Oracle 25.0.5.0.3 notes](https://www.oracle.com/uk/java/technologies/javase/25-0-5-0-3-relnotes.html) (2026-10-24).
- PostgreSQL 2026-11-12 update (18.7, 17.12, …) — we use `postgres:17-alpine` (floating tag): re-pull to get 17.12; CVE list not verified — [postgresql.org](https://www.postgresql.org/support/security/) (2026-11-12).
- pgjdbc CVE-2026-107314 (requireAuth, 42.7.11–42.7.13) and CVE-2026-107315 (padding leak, 42.7.4–42.7.13) — fixed in 42.7.14, which Boot 4.0.9 manages: covered, no further bump — [GHSA-rhp9-mr79-r74h](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-rhp9-mr79-r74h), [GHSA-f64h-wr5q-3qf3](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-f64h-wr5q-3qf3) (2026-10-07).
- pgjdbc GHSA-hggc-j2qh-988m (CVE-2026-107313, <42.7.6) — not affected, Boot 4.0.9 manages 42.7.14 — [advisory](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-hggc-j2qh-988m) (2026-10-07).
- pgjdbc GHSA-hcjv-f5jh-r55v (Low, unbounded SSL cert file read, <=42.7.14, no patch yet): never take `sslrootcert`/`sslcert` paths from untrusted input; set `loginTimeout` — [advisory](https://github.com/pgjdbc/pgjdbc/security/advisories/GHSA-hcjv-f5jh-r55v) (2026-10-15).

## Stack baseline

| Tech | We use | Latest stable | Note |
|------|--------|---------------|------|
| Java | 25 | 25.0.5 (CPU Oct 2026) | [Oracle](https://www.oracle.com/uk/java/technologies/javase/25-0-5-0-3-relnotes.html) |
| Spring Data JPA | managed (4.0.8) | 4.0.8 | Past CVE-2026-47834 fix (4.0.7). |
| Spring Boot (parent) | 4.0.9 | 4.0.10 / 4.1.3 / 4.2.0 | 4.0.10 is a patch with no fix for modules we use; 4.1 / 4.2 are minor upgrades, not done; [releases](https://github.com/spring-projects/spring-boot/releases) (2026-11-19) |
| springdoc-openapi | 3.0.3 | 3.2.0 | Minor upgrade; dependabot PR #30 open. |
| Flyway | managed by Boot (11.14.1) | 13.13.0 | Upgrade with Boot, not alone. |
| PostgreSQL JDBC | managed (42.7.14 in Boot 4.0.9) | 42.7.14 | Latest; see Action needed for unpatched Low GHSA. |
| Micrometer / tracing | managed | 1.18.0 / 1.8.0 | Boot 4.0.9 ships 1.16.8 / 1.6.8. |
| OpenTelemetry OTLP exporter | 1.55.0 | 1.68.0 | Minor; pinned in pom. |
| Mockito | 5.20.0 | 5.24.0 | Minor. |
| Postgres image | 17-alpine | 17.12 | See Action needed. |

Source for "latest": versions-maven-plugin report, 2026-11-20.

## Adopt (new features worth using)

_None recorded yet._

## Avoid / deprecated

- PostgreSQL 14 reaches end of life 2026-11-12 — do not target it — [postgresql.org](https://www.postgresql.org/support/security/).

## Changelog

- 2026-11-14 — Boot 4.0.7→4.0.9 for CVE-2026-47834; added Java CPU and Postgres notes. Not checked (sources unreachable/time): springdoc, Flyway, Testcontainers, GraalVM, OTel advisories, GitHub Actions, Claude Code changelog.
- 2026-11-15 — Checked spring.io/security (newest entries 2026-08-20, none new) and pgjdbc GHSAs: all covered by Boot 4.0.9, no pom change. Not checked: Flyway, Testcontainers, GraalVM, OTel advisories, GitHub Actions, Claude Code changelog.
- 2026-11-16 — spring.io/security (still newest 2026-08-20), pgjdbc GHSAs and Boot releases (4.0.9 / 4.1.2 still latest) checked: only new item is pgjdbc GHSA-hggc-j2qh-988m, not affected; no pom change. Not checked: as 2026-11-15, plus postgresql.org (domain blocked).
- 2026-11-20 — spring.io/security: 8 new advisories (2026-11-19), incl. Spring Security CVE-2026-59273; archetype not affected, no pom change.
- 2026-11-21 — Java4Shell (CVE-2026-114938): JVM images pinned to Temurin 25.0.5.1+2; GraalVM CE 25.0.5.1 not yet published.
- 2026-11-22 — spring.io/security (newest still 2026-11-19), pgjdbc GHSAs (newest 2026-10-15) and Boot releases (4.0.10 / 4.1.3 / 4.2.0 latest): nothing new, no pom change; Spring Security advisories recorded, archetype not affected. Not checked: postgresql.org (blocked), Flyway, Testcontainers, GraalVM, OTel advisories, GitHub Actions, Claude Code changelog.
