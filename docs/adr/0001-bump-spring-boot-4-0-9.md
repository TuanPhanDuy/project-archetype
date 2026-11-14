# 0001 — Bump Spring Boot parent to 4.0.9

- Date: 2026-11-14
- Status: Accepted

## Context
CVE-2026-47834: Spring Data JPA 4.0.0–4.0.6 sort-expression validation bypass (Medium), fixed in 4.0.7 — [advisory](https://spring.io/security/cve-2026-47834). Boot 4.0.7 manages Spring Data JPA 4.0.6, so we are affected; Boot 4.0.8+ ships the fix ([release](https://github.com/spring-projects/spring-boot/releases/tag/v4.0.8)).

## Decision
`spring-boot-starter-parent` 4.0.7 → 4.0.9 in `pom.xml` (same minor).

## Consequences
- Low risk: patch release, 15 unit tests pass on JDK 21.
- Not verified locally: Testcontainers integration tests (no Docker); CI on JDK 25 is the gate.
- Follow-up: Boot 4.1.x is a minor upgrade, deliberately not taken.
