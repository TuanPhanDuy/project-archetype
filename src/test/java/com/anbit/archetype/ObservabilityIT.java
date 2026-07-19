package com.anbit.archetype;

import static org.assertj.core.api.Assertions.assertThat;

import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.dto.ProductRequest;
import com.anbit.archetype.dto.ProductResponse;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * Verifies the observability wiring is live: the health probe responds and the Prometheus
 * scrape endpoint exposes JVM/HTTP metrics, the application tag, and our custom
 * {@code orders_placed_total} business metric.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class ObservabilityIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    @Test
    void healthEndpointReportsUp() {
        client.get().uri("/actuator/health")
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("UP");
    }

    @Test
    void prometheusEndpointExposesMetricsIncludingCustomCounter() {
        // Drive the custom counter at least once.
        UUID productId = client.post().uri("/api/v1/products")
                .body(new ProductRequest("Metric Widget", null, new BigDecimal("1.00"), null))
                .exchange().expectStatus().isCreated()
                .expectBody(ProductResponse.class).returnResult().getResponseBody().id();
        CreateOrderRequest.Item item = new CreateOrderRequest.Item(productId, 1);
        CreateOrderRequest order = new CreateOrderRequest("Metrics", List.of(item));
        client.post().uri("/api/v1/orders")
                .body(order)
                .exchange().expectStatus().isCreated();

        String body = client.get().uri("/actuator/prometheus")
                .exchange()
                .expectStatus().isOk()
                .expectBody(String.class)
                .returnResult().getResponseBody();

        assertThat(body).isNotNull();
        assertThat(body).contains("orders_placed_total");
        assertThat(body).contains("application=\"service-archetype\"");
        assertThat(body).contains("jvm_threads_live_threads");
    }
}
