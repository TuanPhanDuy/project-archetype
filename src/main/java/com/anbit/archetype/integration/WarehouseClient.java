package com.anbit.archetype.integration;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.service.annotation.GetExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/** HTTP client for the warehouse system. Use it through {@link WarehouseGateway}. */
@HttpExchange("/api/v1/shipments")
public interface WarehouseClient {

    @PostExchange
    WarehouseShipmentResponse createShipment(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @RequestBody WarehouseShipmentRequest request);

    @GetExchange("/{shipmentId}")
    WarehouseShipmentResponse getShipment(@PathVariable String shipmentId);
}
