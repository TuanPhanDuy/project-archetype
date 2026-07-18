package com.anbit.archetype.controller;

import com.anbit.archetype.TestcontainersConfiguration;
import com.anbit.archetype.dto.CategoryRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Exercises the centralised exception handling: a unique-constraint clash maps to 409 and a
 * malformed path variable maps to 400 — both as RFC 9457 ProblemDetail responses.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class CategoryControllerIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void duplicateCategoryNameReturnsConflict() {
        CategoryRequest request = new CategoryRequest("Electronics-" + port, "first");
        client.post().uri("/api/v1/categories").body(request).exchange().expectStatus().isCreated();

        client.post().uri("/api/v1/categories").body(request)
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.title").isEqualTo("Data integrity violation")
                .jsonPath("$.type").isEqualTo("urn:problem:data-integrity")
                .jsonPath("$.timestamp").exists();
    }

    @Test
    void malformedUuidPathReturnsBadRequest() {
        client.get().uri("/api/v1/categories/not-a-uuid")
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.timestamp").exists();   // enriched by the global handler
    }
}
