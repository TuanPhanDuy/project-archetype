# Tech radar

Shared, dated knowledge for the agents: what's current, what's risky, what to adopt.
Refreshed daily by `.github/workflows/knowledge-refresh.yml` (via `/refresh-knowledge`) and
merged only after human review. Prefer it over training data; verify with Context7 when unsure.

**Last checked:** 2026-10-26 (initial seed, manual)

## Action needed (security)

_None recorded yet._ Format: `CVE / advisory — affected artifact@version — fixed in — source`.

## Stack baseline

| Tech | We use | Latest stable | Note |
|------|--------|---------------|------|
| Java | 25 | | |
| Spring Boot (parent) | 4.0.7 | 4.1.2 | Minor upgrade; review release notes first. |
| springdoc-openapi | 3.0.3 | 3.2.0 | |
| Flyway | 11.14.1 | 13.11.1 | Two majors behind — managed by Boot; upgrade with Boot. |
| PostgreSQL JDBC | 42.7.11 | 42.7.14 | Patch. |
| OpenTelemetry OTLP exporter | 1.55.0 | 1.67.0 | |
| Mockito | 5.20.0 | 5.24.0 | |
| Postgres image (tests/compose) | 17-alpine | | |

## Adopt (new features worth using)

_None recorded yet._

## Avoid / deprecated

_None recorded yet._

## Changelog

- 2026-10-26 — Seeded from `versions-maven-plugin` report.
