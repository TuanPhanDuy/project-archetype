package com.anbit.archetype.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.anbit.archetype.TestcontainersConfiguration;
import com.anbit.archetype.dto.ProductRequest;
import com.anbit.archetype.dto.ProductResponse;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end test of the product API against a real Postgres + real Flyway migrations.
 * This is the kind of test that catches schema/constraint bugs an H2 in-memory DB hides.
 *
 * <p>Uses Spring Framework 7's {@link RestTestClient} (the servlet test client that
 * replaced {@code TestRestTemplate}/{@code WebTestClient} for MVC in Spring Boot 4 — no
 * reactive dependency required).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ProductControllerIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void createsAndFetchesProduct() {
        ProductRequest request =
                new ProductRequest("Widget", "A useful widget", new BigDecimal("19.99"), null);

        ProductResponse created = client.post().uri("/api/v1/products")
                .body(request)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .returnResult()
                .getResponseBody();

        assertThat(created).isNotNull();
        assertThat(created.id()).isNotNull();
        assertThat(created.name()).isEqualTo("Widget");

        client.get().uri("/api/v1/products/{id}", created.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.name").isEqualTo("Widget")
                .jsonPath("$.price").isEqualTo(19.99);
    }

    @Test
    void rejectsInvalidPayload() {
        ProductRequest invalid = new ProductRequest("", null, new BigDecimal("-1"), null);

        client.post().uri("/api/v1/products")
                .body(invalid)
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Validation failed");
    }

    @Test
    void returnsProblemDetailForMissingProduct() {
        client.get().uri("/api/v1/products/{id}", "00000000-0000-0000-0000-000000000000")
                .exchange()
                .expectStatus().isNotFound()
                .expectBody()
                .jsonPath("$.title").isEqualTo("Resource not found");
    }
}
