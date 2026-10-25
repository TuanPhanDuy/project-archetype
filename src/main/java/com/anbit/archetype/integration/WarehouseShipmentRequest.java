package com.anbit.archetype.integration;

import java.util.List;
import java.util.UUID;

/** Warehouse's request contract (not our API dto). */
public record WarehouseShipmentRequest(UUID orderId, String recipient, List<Line> lines) {

    public record Line(UUID productId, int quantity) {}
}
