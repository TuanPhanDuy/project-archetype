package com.anbit.archetype.dto;

import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/** Lightweight order view for list endpoints — no line items (avoids fetching collections). */
public record OrderSummaryResponse(
        UUID id,
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt) {

    public static OrderSummaryResponse from(Order order) {
        return new OrderSummaryResponse(
                order.getId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt());
    }
}
