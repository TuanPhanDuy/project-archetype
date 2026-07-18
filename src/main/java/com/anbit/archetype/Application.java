package com.anbit.archetype;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Application entry point.
 *
 * <p>Conventions enforced across this archetype:
 * <ul>
 *   <li>Package-by-feature: each business capability lives in its own package
 *       (see {@code product}) holding its controller, service, repository, entity and DTOs.</li>
 *   <li>Cross-cutting concerns live under {@code common} and {@code config}.</li>
 *   <li>Errors are returned as RFC 9457 {@code ProblemDetail} responses.</li>
 * </ul>
 */
@SpringBootApplication
public class Application {

    public static void main(String[] args) {
        SpringApplication.run(Application.class, args);
    }
}
