# 0003 — Pin Temurin base images to 25.0.5.1 (Java4Shell)

- Date: 2026-11-21
- Status: Accepted

## Context
CVE-2026-114938 ("Java4Shell"): memory corruption in the Base64 intrinsic, remote code execution on x86-64 CPUs with AVX-512 VBMI (ARM64 not affected), CVSS 8.1. JDK 25 up to 25.0.5.0.3 is affected; fixed in 25.0.5.1 (Oracle and Eclipse Temurin) — [Tenable FAQ](https://www.tenable.com/blog/java4shell-cve-2026-114938-frequently-asked-questions-about-openjdk-rce-vulnerability), [Red Hat RHSA-2026:100573](https://access.redhat.com/errata/RHSA-2026:100573) (2026-11-18). `Dockerfile.jvm` used the floating `eclipse-temurin:25-jdk/-jre` tags, so whether an image was patched depended on when it was built and on the builder's cache.

## Decision
Pin `Dockerfile.jvm` to `eclipse-temurin:25.0.5.1_2-jdk` / `-jre` (Temurin 25.0.5.1+2, published 2026-11-18) and add Dependabot `docker` updates so later patch builds arrive as PRs.

## Consequences
- JVM images are patched regardless of build cache; rebuild and redeploy every service image.
- The native `Dockerfile` stays on `ghcr.io/graalvm/native-image-community:25`: no GraalVM Community 25.0.5.1 image exists yet, and no source says whether GraalVM for JDK 25 / native-image is affected. Revisit when one is published.
- Interim mitigation for any JVM that cannot be upgraded yet: `-XX:-UseBASE64Intrinsics`.
