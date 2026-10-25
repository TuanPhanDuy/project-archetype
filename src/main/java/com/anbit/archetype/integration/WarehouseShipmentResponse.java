package com.anbit.archetype.integration;

/** A shipment as the warehouse returns it. */
public record WarehouseShipmentResponse(String shipmentId, String status) {}
