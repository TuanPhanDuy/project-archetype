package com.anbit.archetype.integration;

import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;

/**
 * Calls the warehouse with retries on transient errors (5xx, I/O) only. Retrying the POST is
 * safe because every attempt sends the same Idempotency-Key.
 */
@Component
public class WarehouseGateway {

    private final WarehouseClient client;

    public WarehouseGateway(WarehouseClient client) {
        this.client = client;
    }

    @Retryable(
            includes = {HttpServerErrorException.class, ResourceAccessException.class},
            maxRetriesString = "${app.integration.warehouse.retry.max-retries:3}",
            delayString = "${app.integration.warehouse.retry.delay-ms:200}",
            multiplier = 2,
            maxDelay = 2000)
    public WarehouseShipmentResponse requestShipment(WarehouseShipmentRequest request) {
        return client.createShipment(request.orderId().toString(), request);
    }

    @Retryable(
            includes = {HttpServerErrorException.class, ResourceAccessException.class},
            maxRetriesString = "${app.integration.warehouse.retry.max-retries:3}",
            delayString = "${app.integration.warehouse.retry.delay-ms:200}",
            multiplier = 2,
            maxDelay = 2000)
    public WarehouseShipmentResponse getShipment(String shipmentId) {
        return client.getShipment(shipmentId);
    }
}
