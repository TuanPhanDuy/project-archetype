---
name: qa-validate
description: >
  Hunts down real bugs in {{PROJECT_PURPOSE}} by actually running the service and driving
  its real interface (HTTP over curl/RestTestClient, or a UI if one exists) — never by
  reading source and predicting behavior — then writes the regression test that proves each
  confirmed bug and proves the fix. Use after implementing a feature or fix, when asked to
  "validate", "QA this", "find bugs", "write test cases for this", or before reporting any
  change as done. Complements `test-engineer` (phase 4, which proves agreed acceptance
  criteria): this skill is for ad hoc bug-hunting and regression coverage, callable any time.
---

# QA validate

Core rule: **a bug is not confirmed, and a fix is not proven, until you've watched the real
service run and read its actual response.** Reading code and predicting what it does is a
hypothesis, not validation. If a report or diff touches behavior that can be exercised over
HTTP, exercise it over HTTP against a live instance — don't settle for calling a `@Service`
method directly in isolation when an `*IT` test could hit the real endpoint through the real
stack (web layer, validation, Testcontainers Postgres, Flyway-applied schema).

## Workflow

1. **Scope.** Given a bug report, extract exact repro steps (endpoint, payload, expected vs.
   actual). Given a diff/feature, enumerate the changed code paths and the acceptance
   criteria they're supposed to satisfy.
2. **Get the real thing running.** Check for a project `verify` skill or the README's run
   instructions first and reuse them. Otherwise:
   - `./mvnw spring-boot:run` (or `docker compose up` per `compose.yaml`) to get a live
     instance, then hit it with `curl` using the exact repro payload — or drive it via
     Spring Framework 7's `RestTestClient` inside a `@SpringBootTest(RANDOM_PORT)` +
     Testcontainers test, per `test-engineer`'s conventions (`@Import(TestcontainersConfiguration.class)`,
     `@ServiceConnection`, real Postgres, real Flyway — never H2).
   - If this service exposes a UI, drive it like a user would (browser automation against the
     running page) rather than asserting against isolated component logic.
   - `k6/smoke.js` can double as a quick live-endpoint sanity probe before deeper manual repro.
3. **Reproduce, don't assume.** Run the real flow with the real input; capture the actual
   response (status, body, `ProblemDetail` title/type on errors) next to what was expected.
   A mismatch is a *confirmed* bug. Anything you genuinely couldn't execute (no Docker, no
   external dependency available) is *unverified* — say so; never present it as "probably
   fine."
4. **Pin root cause to file:line** before writing a test. A test for a bug you don't
   understand usually encodes the wrong assertion.
5. **Write one regression test per confirmed bug**, following `test-engineer`'s split: pure
   `*Test` (JUnit 5 + Mockito + AssertJ, no Spring context) for business-logic bugs; `*IT`
   (Testcontainers + `RestTestClient`) for anything that only manifests through the real HTTP
   stack. The test must fail against the pre-fix code and pass after — if you can't make it
   fail first, it isn't testing the bug.
6. **Re-run the same real drive from step 2–3** after fixing — a green `./mvnw verify` is not
   sufficient proof by itself; confirm the actual symptom is gone the way you found it.
7. **Report**: confirmed bugs (file:line, repro, root cause), tests added (path), anything
   left unverified and why. Don't claim "works" without showing the command/response that
   proves it. Hand off failures found this way to `senior-developer` the same as
   `test-engineer` would.

## Guardrails

- Anything created while driving a real run (test records, users, queue messages) is a
  disposable fixture against a Testcontainers-managed Postgres, not production data — but
  still name/tag it identifiably and clean it up if it outlives the test container (e.g. a
  manual `curl` run against a long-lived dev instance). Target cleanup by the **exact ID you
  created**, never a blanket delete-by-status query — a broad "remove everything matching
  this state" is exactly the kind of action that risks a teammate's real data on a shared
  dev/staging instance and should be treated with the same caution as any other destructive
  operation.
- If a live dependency you don't control (payment gateway, third-party API) is in the path,
  say so and validate at the nearest controllable boundary (Testcontainers/WireMock) rather
  than skipping validation entirely.

## When you truly cannot run it

No Docker/sandbox available: fall back to static review — trace each changed path by hand,
enumerate edge cases, write tests for them — but say explicitly "not executed, static review
only" in the report so it isn't mistaken for a live-verified result.
