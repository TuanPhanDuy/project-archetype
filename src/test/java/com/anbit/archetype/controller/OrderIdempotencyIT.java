package com.anbit.archetype.controller;

import static org.assertj.core.api.Assertions.assertThat;

import com.anbit.archetype.TestcontainersConfiguration;
import com.anbit.archetype.domain.OrderStatus;
import com.anbit.archetype.dto.CreateOrderRequest;
import com.anbit.archetype.dto.OrderResponse;
import com.anbit.archetype.dto.ProductRequest;
import com.anbit.archetype.dto.ProductResponse;
import com.anbit.archetype.repository.IdempotencyKeyRepository;
import com.anbit.archetype.repository.OrderRepository;
import com.anbit.archetype.service.OrderService;
import io.micrometer.core.instrument.MeterRegistry;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.client.RestTestClient;

/**
 * End-to-end coverage of the {@code Idempotency-Key} header on {@code POST /api/v1/orders}
 * against real Postgres + Flyway: new key, same-body replay, different-body conflict, the
 * genuine concurrent-insert race, and the header's validation/no-op edge cases.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestcontainersConfiguration.class)
class OrderIdempotencyIT {

    @LocalServerPort
    int port;

    RestTestClient client;

    @Autowired
    OrderRepository orderRepository;

    @Autowired
    IdempotencyKeyRepository idempotencyKeyRepository;

    @Autowired
    OrderService orderService;

    @Autowired
    MeterRegistry meterRegistry;

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

    // ---- AC1: new key + valid body -> 201, exactly one orders row and one idempotency_key row ----

    @Test
    void newIdempotencyKeyCreatesOrderAndIdempotencyRow() {
        UUID productId = createProduct("Idem Widget", "20.00");
        String key = UUID.randomUUID().toString();
        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyKeyRepository.count();

        OrderResponse order = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .body(new CreateOrderRequest("Ada", List.of(new CreateOrderRequest.Item(productId, 1))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(order).isNotNull();
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(keysBefore + 1);
        assertThat(idempotencyKeyRepository.findById(key)).isPresent()
                .get().satisfies(row -> assertThat(row.getOrderId()).isEqualTo(order.id()));
    }

    // ---- AC2: replay with same key + same body -> same order id/Location, current state, no duplicate rows ----

    @Test
    void replayingSameKeyAndBodyReturnsCurrentStateNotFrozenCopy() {
        UUID productId = createProduct("Replay Widget", "15.00");
        String key = UUID.randomUUID().toString();
        CreateOrderRequest body = new CreateOrderRequest("Grace", List.of(new CreateOrderRequest.Item(productId, 2)));
        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyKeyRepository.count();

        var firstResult = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult();
        OrderResponse first = firstResult.getResponseBody();
        assertThat(first).isNotNull();
        assertThat(first.status()).isEqualTo(OrderStatus.CREATED);
        URI firstLocation = firstResult.getResponseHeaders().getLocation();
        assertThat(firstLocation).isNotNull();

        // Mutate the order's state between the two calls: the replay must reflect the CURRENT
        // persisted state, not a snapshot frozen at creation time.
        orderService.markFulfilling(first.id());

        var secondResult = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult();
        OrderResponse second = secondResult.getResponseBody();
        URI secondLocation = secondResult.getResponseHeaders().getLocation();

        assertThat(second).isNotNull();
        assertThat(second.id()).isEqualTo(first.id());
        assertThat(secondLocation).isEqualTo(firstLocation);
        assertThat(second.status()).isEqualTo(OrderStatus.FULFILLING);

        // No new order or idempotency-key row was created by the replay.
        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(keysBefore + 1);
    }

    // ---- AC3: replay with same key + different body -> 409 conflict, nothing leaked ----

    @Test
    void replayingSameKeyWithDifferentBodyReturns409WithoutLeakingDetails() {
        UUID productId = createProduct("Conflict Widget", "8.00");
        UUID otherProductId = createProduct("Other Conflict Widget", "3.00");
        String key = UUID.randomUUID().toString();

        OrderResponse original = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .body(new CreateOrderRequest("Ada", List.of(new CreateOrderRequest.Item(productId, 1))))
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();
        assertThat(original).isNotNull();

        byte[] raw = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", key)
                .body(new CreateOrderRequest("Bella", List.of(new CreateOrderRequest.Item(otherProductId, 5))))
                .exchange()
                .expectStatus().isEqualTo(409)
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:problem:idempotency-key-reuse")
                .jsonPath("$.title").isEqualTo("Idempotency key reuse")
                .jsonPath("$.status").isEqualTo(409)
                .returnResult().getResponseBody();

        assertThat(raw).isNotNull();
        String responseBody = new String(raw, StandardCharsets.UTF_8);

        // Must not echo the stored hash, the conflicting request body, or the original order.
        assertThat(responseBody).doesNotContainPattern("[0-9a-f]{64}");
        assertThat(responseBody).doesNotContain("Bella");
        assertThat(responseBody).doesNotContain(otherProductId.toString());
        assertThat(responseBody).doesNotContain("Ada");
        assertThat(responseBody).doesNotContain(original.id().toString());
        assertThat(responseBody).doesNotContain(productId.toString());
    }

    // ---- AC4: genuine concurrent race on a brand-new key -> exactly one order, counter +1 ----

    @Test
    void concurrentRequestsWithSameNewKeyPersistExactlyOneOrder() throws Exception {
        UUID productId = createProduct("Race Widget", "9.99");
        String key = UUID.randomUUID().toString();
        CreateOrderRequest body = new CreateOrderRequest("Racer", List.of(new CreateOrderRequest.Item(productId, 1)));

        long ordersBefore = orderRepository.count();
        long keysBefore = idempotencyKeyRepository.count();
        double placedBefore = meterRegistry.get("orders.placed").counter().count();

        int threadCount = 2;
        ExecutorService pool = Executors.newFixedThreadPool(threadCount);
        CountDownLatch ready = new CountDownLatch(threadCount);
        CountDownLatch start = new CountDownLatch(1);

        Callable<UUID> task = () -> {
            ready.countDown();
            start.await();
            return client.post().uri("/api/v1/orders")
                    .header("Idempotency-Key", key)
                    .body(body)
                    .exchange()
                    .expectStatus().isCreated()
                    .expectBody(OrderResponse.class)
                    .returnResult().getResponseBody().id();
        };

        try {
            List<Future<UUID>> futures = IntStream.range(0, threadCount)
                    .mapToObj(i -> pool.submit(task))
                    .toList();
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();

            List<UUID> ids = futures.stream().map(f -> {
                try {
                    return f.get(20, TimeUnit.SECONDS);
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
            }).toList();

            assertThat(ids).hasSize(threadCount);
            assertThat(ids).allSatisfy(id -> assertThat(id).isEqualTo(ids.get(0)));
        } finally {
            pool.shutdown();
        }

        assertThat(orderRepository.count()).isEqualTo(ordersBefore + 1);
        assertThat(idempotencyKeyRepository.count()).isEqualTo(keysBefore + 1);
        assertThat(meterRegistry.get("orders.placed").counter().count()).isEqualTo(placedBefore + 1.0);
    }

    // ---- AC5: no header / blank / whitespace-only header behave like no idempotency tracking ----

    @Test
    void noHeaderCreatesOrderWithoutIdempotencyTracking() {
        UUID productId = createProduct("No Header Widget", "4.00");
        long keysBefore = idempotencyKeyRepository.count();

        client.post().uri("/api/v1/orders")
                .body(new CreateOrderRequest("NoHeader", List.of(new CreateOrderRequest.Item(productId, 1))))
                .exchange()
                .expectStatus().isCreated();

        assertThat(idempotencyKeyRepository.count()).isEqualTo(keysBefore);
    }

    @Test
    void blankOrWhitespaceIdempotencyKeyBehavesLikeNoKeyAndDoesNotDedupe() {
        UUID productId = createProduct("Blank Header Widget", "4.00");
        CreateOrderRequest body = new CreateOrderRequest("Blanky", List.of(new CreateOrderRequest.Item(productId, 1)));
        long keysBefore = idempotencyKeyRepository.count();

        OrderResponse first = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", "   ")
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        OrderResponse second = client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", "")
                .body(body)
                .exchange()
                .expectStatus().isCreated()
                .expectBody(OrderResponse.class)
                .returnResult().getResponseBody();

        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        // Blank keys are never tracked, so replaying the same body creates a SECOND order.
        assertThat(second.id()).isNotEqualTo(first.id());
        assertThat(idempotencyKeyRepository.count()).isEqualTo(keysBefore);
    }

    @Test
    void idempotencyKeyOver255CharsReturns400Validation() {
        UUID productId = createProduct("Too Long Key Widget", "4.00");
        String tooLong = "a".repeat(256);

        client.post().uri("/api/v1/orders")
                .header("Idempotency-Key", tooLong)
                .body(new CreateOrderRequest("TooLong", List.of(new CreateOrderRequest.Item(productId, 1))))
                .exchange()
                .expectStatus().isBadRequest()
                .expectBody()
                .jsonPath("$.type").isEqualTo("urn:problem:validation")
                .jsonPath("$.title").isEqualTo("Validation failed");
    }
}
