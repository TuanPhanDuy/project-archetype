package com.onemount.archetype;

import org.springframework.boot.SpringApplication;

/**
 * Local-development launcher: runs the real {@link Application} but with the
 * {@link TestcontainersConfiguration} Postgres wired in via {@code @ServiceConnection},
 * so the app boots against a throwaway Docker Postgres (real Flyway migrations and all)
 * without needing {@code docker compose up} or any local datasource config.
 *
 * <p>Run it from the IDE (run {@code TestApplication.main}) or the command line:
 * <pre>{@code  ./mvnw spring-boot:test-run}</pre>
 *
 * This is the development-time companion to the {@code *IT} tests, which use the same
 * container configuration under JUnit.
 */
public class TestApplication {

    public static void main(String[] args) {
        SpringApplication.from(Application::main)
                .with(TestcontainersConfiguration.class)
                .run(args);
    }
}
