# 0003 — Pin Temurin base images to 25.0.5.1 (Java4Shell)

- Date: 2026-11-21
- Status: Accepted

## Context
CVE-2026-114938 ("Java4Shell"): JDK 25 up to 25.0.5.0.3 is affected, fixed in 25.0.5.1 — [Tenable FAQ](https://www.tenable.com/blog/java4shell-cve-2026-114938-frequently-asked-questions-about-openjdk-rce-vulnerability), [RHSA-2026:100573](https://access.redhat.com/errata/RHSA-2026:100573). `Dockerfile.jvm` used the floating `eclipse-temurin:25-jdk/-jre` tags, so whether an image was patched depended on when it was built and on the builder's cache.

## Decision
Pin `Dockerfile.jvm` to `eclipse-temurin:25.0.5.1_2-jdk` / `-jre` (Temurin 25.0.5.1+2: binaries 2026-11-18, Docker image 2026-11-20) and add Dependabot `docker` updates so later patch builds arrive as PRs.

## Consequences
- JVM images are patched regardless of build cache; rebuild and redeploy every service image.
- The native `Dockerfile` stays on `ghcr.io/graalvm/native-image-community:25`: no GraalVM Community 25.0.5.1 image exists yet, and the advisories list GraalVM as affected only for JDK 17 and 21, with no JDK 25 entry. Revisit when a 25.0.5.1 image is published.
- Interim mitigation for a JVM that cannot be upgraded yet: `-XX:-UseBASE64Intrinsics` (named by Calif and Tenable, not in Oracle's or OpenJDK's advisory; a product flag, no unlock needed).
