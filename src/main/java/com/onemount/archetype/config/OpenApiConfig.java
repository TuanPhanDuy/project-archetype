package com.onemount.archetype.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI document metadata. Per-endpoint docs are generated automatically from the
 * controllers, DTO records, and bean-validation constraints; annotate handlers with
 * {@code @Operation}/{@code @Tag} (see the controllers) to enrich them.
 *
 * <p>Defaults: spec at {@code /v3/api-docs} (+ {@code .yaml}), Swagger UI at
 * {@code /swagger-ui.html}. The UI is disabled in the {@code prod} profile.
 */
@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI serviceOpenApi(@Value("${spring.application.name}") String appName) {
        return new OpenAPI().info(new Info()
                .title(appName + " API")
                .version("v1")
                .description("""
                        REST API for the %s service. \
                        Errors are returned as RFC 9457 problem details (application/problem+json).\
                        """.formatted(appName))
                .contact(new Contact().name("Platform Team").email("team@example.com"))
                .license(new License().name("Proprietary")));
    }
}
