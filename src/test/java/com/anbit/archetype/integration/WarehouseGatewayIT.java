package com.anbit.archetype.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.anbit.archetype.config.HttpClientConfig;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.ImportAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.HttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.imperative.ImperativeHttpClientAutoConfiguration;
import org.springframework.boot.http.client.autoconfigure.service.HttpServiceClientPropertiesAutoConfiguration;
import org.springframework.boot.http.converter.autoconfigure.HttpMessageConvertersAutoConfiguration;
import org.springframework.boot.jackson.autoconfigure.JacksonAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.RestClientAutoConfiguration;
import org.springframework.boot.restclient.autoconfigure.service.HttpServiceClientAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

/** WarehouseGateway against a real HTTP stub; loads only the HTTP-client slice (no Docker). */
@SpringBootTest(classes = {HttpClientConfig.class, WarehouseGateway.class})
@ImportAutoConfiguration({
        JacksonAutoConfiguration.class,
        HttpMessageConvertersAutoConfiguration.class,
        HttpClientAutoConfiguration.class,
        ImperativeHttpClientAutoConfiguration.class,
        HttpServiceClientPropertiesAutoConfiguration.class,
        RestClientAutoConfiguration.class,
        HttpServiceClientAutoConfiguration.class})
class WarehouseGatewayIT {

    private static final int MAX_RETRIES = 2;

    private static final HttpServer warehouse = startStub();
    /** One response per request; the last one repeats. */
    private static final Deque<Response> script = new ArrayDeque<>();
    private static final List<Recorded> requests = new CopyOnWriteArrayList<>();

    @Autowired
    WarehouseGateway gateway;

    @DynamicPropertySource
    static void warehouseProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.http.serviceclient.warehouse.base-url",
                () -> "http://localhost:" + warehouse.getAddress().getPort());
        registry.add("app.integration.warehouse.retry.max-retries", () -> MAX_RETRIES);
        registry.add("app.integration.warehouse.retry.delay-ms", () -> 10);
    }

    @BeforeEach
    void reset() {
        script.clear();
        requests.clear();
    }

    @AfterAll
    static void stopStub() {
        warehouse.stop(0);
    }

    @Test
    void requestShipmentPostsOrderAndSendsOrderIdAsIdempotencyKey() {
        respond(201, "{\"shipmentId\":\"SHP-1\",\"status\":\"CREATED\"}");
        UUID orderId = UUID.randomUUID();

        WarehouseShipmentResponse shipment = gateway.requestShipment(request(orderId));

        assertThat(shipment).isEqualTo(new WarehouseShipmentResponse("SHP-1", "CREATED"));
        assertThat(requests).singleElement().satisfies(r -> {
            assertThat(r.method()).isEqualTo("POST");
            assertThat(r.path()).isEqualTo("/api/v1/shipments");
            assertThat(r.idempotencyKey()).isEqualTo(orderId.toString());
            assertThat(r.body()).contains(orderId.toString(), "\"quantity\":2");
        });
    }

    @Test
    void getShipmentReadsByIdFromPath() {
        respond(200, "{\"shipmentId\":\"SHP-7\",\"status\":\"SHIPPED\"}");

        assertThat(gateway.getShipment("SHP-7").status()).isEqualTo("SHIPPED");
        assertThat(requests).singleElement()
                .extracting(Recorded::path).isEqualTo("/api/v1/shipments/SHP-7");
    }

    @Test
    void transientServerErrorIsRetriedUntilItSucceeds() {
        respond(503, "");
        respond(503, "");
        respond(201, "{\"shipmentId\":\"SHP-2\",\"status\":\"CREATED\"}");
        UUID orderId = UUID.randomUUID();

        assertThat(gateway.requestShipment(request(orderId)).shipmentId()).isEqualTo("SHP-2");
        assertThat(requests).hasSize(3)
                .extracting(Recorded::idempotencyKey).containsOnly(orderId.toString());
    }

    @Test
    void persistentServerErrorGivesUpAfterMaxRetries() {
        respond(500, "");

        assertThatThrownBy(() -> gateway.requestShipment(request(UUID.randomUUID())))
                .isInstanceOf(HttpServerErrorException.class);
        assertThat(requests).hasSize(1 + MAX_RETRIES);
    }

    @Test
    void clientErrorIsNotRetried() {
        respond(400, "{\"error\":\"unknown product\"}");

        assertThatThrownBy(() -> gateway.requestShipment(request(UUID.randomUUID())))
                .isInstanceOf(HttpClientErrorException.BadRequest.class);
        assertThat(requests).hasSize(1);
    }

    // ---- stub plumbing ----

    private static WarehouseShipmentRequest request(UUID orderId) {
        return new WarehouseShipmentRequest(orderId, "Ada",
                List.of(new WarehouseShipmentRequest.Line(UUID.randomUUID(), 2)));
    }

    private static void respond(int status, String body) {
        script.addLast(new Response(status, body));
    }

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                requests.add(new Recorded(exchange.getRequestMethod(),
                        exchange.getRequestURI().getPath(),
                        exchange.getRequestHeaders().getFirst("Idempotency-Key"),
                        new String(exchange.getRequestBody().readAllBytes(),
                                StandardCharsets.UTF_8)));
                Response next = script.size() > 1 ? script.pollFirst() : script.peekFirst();
                byte[] bytes = next.body().getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(next.status(), bytes.length == 0 ? -1 : bytes.length);
                if (bytes.length > 0) {
                    exchange.getResponseBody().write(bytes);
                }
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
    }

    private record Response(int status, String body) {}

    private record Recorded(String method, String path, String idempotencyKey, String body) {}
}
