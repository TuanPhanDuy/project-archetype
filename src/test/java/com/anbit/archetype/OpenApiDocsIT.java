package com.anbit.archetype;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Verifies springdoc serves a valid OpenAPI spec and the Swagger UI under Spring Boot 4 /
 * Jackson 3 — if springdoc still pulled Jackson 2, the context wouldn't even start.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OpenApiDocsIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void apiDocsServeTheOpenApiSpec() {
        client.get().uri("/v3/api-docs")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.openapi").exists()                          // e.g. "3.1.0"
                .jsonPath("$.info.title").isEqualTo("service-archetype API")
                .jsonPath("$.paths['/api/v1/products']").exists()
                .jsonPath("$.paths['/api/v1/orders']").exists();
    }

    @Test
    void swaggerUiIsServed() {
        client.get().uri("/swagger-ui/index.html")
                .exchange()
                .expectStatus().isOk();
    }
}
