package com.anbit.archetype.config;

import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Application-wide bean wiring. Injecting a {@link Clock} (instead of calling
 * {@code Instant.now()} directly) keeps time-dependent logic deterministic in tests.
 */
@Configuration
public class ApplicationConfig {

    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
