package com.onemount.archetype.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import com.onemount.archetype.TestcontainersConfiguration;
import com.onemount.archetype.domain.JobStatus;
import com.onemount.archetype.domain.OrderStatus;
import com.onemount.archetype.dto.CreateOrderRequest;
import com.onemount.archetype.dto.JobResponse;
import com.onemount.archetype.dto.OrderResponse;
import com.onemount.archetype.dto.ProductRequest;
import com.onemount.archetype.dto.ProductResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end coverage of both processing styles against real Postgres + Flyway:
 * the synchronous order creation and the asynchronous fulfillment (202 + job polling).
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderControllerIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @BeforeEach
    void setUp() {
        client = RestTestClient.bindToServer().baseUrl("http://localhost:" + port).build();
    }

    private UUID createProduct(String name, String price) {
        ProductResponse product = client.post().uri("/api/v1/products")
                .body(new ProductRequest(name, null, new BigDecimal(price), null))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(ProductResponse.class)
                .returnResult().getResponseBody();
        assertThat(product).isNotNull();
        return product.id();
    }

    @Test
    void createsOrderSynchronouslyWithComputedTotal() {
        UUID productId = createProduct("Sync Widget", "12.50");

        OrderResponse order = client.post().uri("/api/v1/orders")
                .body(new CreateOrderRequest("Grace", List.of(new CreateOrderRequest.Item(productId, 3))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.CREATED);
        assertThat(order.items()).hasSize(1);
        assertThat(order.totalAmount()).isEqualByComparingTo("37.50"); // 3 * 12.50
    }

    @Test
    void fulfillsOrderAsynchronouslyAndPollsJobToCompletion() {
        UUID productId = createProduct("Async Widget", "5.00");
        OrderResponse order = client.post().uri("/api/v1/orders")
                .body(new CreateOrderRequest("Linus", List.of(new CreateOrderRequest.Item(productId, 2))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();
        assertThat(order).isNotNull();

        // Async kick-off: 202 Accepted with a job to poll.
        JobResponse job = client.post().uri("/api/v1/orders/{id}/fulfillment", order.id())
                .exchange()
                .expectStatus().isEqualTo(202)
                .expectBody(JobResponse.class)
                .returnResult().getResponseBody();
        assertThat(job).isNotNull();
        assertThat(job.status()).isIn(JobStatus.PENDING, JobStatus.RUNNING);

        // Poll the job until the background worker finishes.
        await().atMost(Duration.ofSeconds(10)).pollInterval(Duration.ofMillis(100)).untilAsserted(() ->
                client.get().uri("/api/v1/jobs/{id}", job.id())
                        .exchange()
                        .expectStatus().isOk()
                        .expectBody()
                        .jsonPath("$.status").isEqualTo("COMPLETED"));

        // The order reflects the completed async work.
        client.get().uri("/api/v1/orders/{id}", order.id())
                .exchange()
                .expectStatus().isOk()
                .expectBody()
                .jsonPath("$.status").isEqualTo("FULFILLED");
    }
}
