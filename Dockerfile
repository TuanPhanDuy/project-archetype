# syntax=docker/dockerfile:1
#
# GraalVM native image build. Produces a tiny, fast-starting (<100ms) executable.
# Build:  docker build -t service-archetype:native .
#
# Prefer this for production. For a quicker JVM image instead, see Dockerfile.jvm.

# ---- Build stage: compile to a native executable ----
FROM ghcr.io/graalvm/native-image-community:25 AS build
WORKDIR /workspace

# Maven wrapper + pom first so dependency resolution is cached across source changes.
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN ./mvnw -B dependency:go-offline

COPY src/ src/
RUN ./mvnw -B -Pnative native:compile -DskipTests

# ---- Runtime stage: distroless, no JVM ----
FROM gcr.io/distroless/base-debian12:nonroot
WORKDIR /app
COPY --from=build /workspace/target/service-archetype ./service-archetype
EXPOSE 8080
USER nonroot
ENTRYPOINT ["./service-archetype"]
