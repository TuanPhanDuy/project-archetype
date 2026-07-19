package com.anbit.archetype.dto;

import com.anbit.archetype.domain.Order;
import com.anbit.archetype.domain.OrderItem;
import com.anbit.archetype.domain.OrderStatus;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Full order representation including its line items. */
public record OrderResponse(
        UUID id,
        String customerName,
        OrderStatus status,
        BigDecimal totalAmount,
        Instant createdAt,
        List<Item> items) {

    public record Item(
            UUID productId,
            String productName,
            int quantity,
            BigDecimal unitPrice,
            BigDecimal lineTotal) {

        static Item from(OrderItem item) {
            return new Item(
                    item.getProduct().getId(),
                    item.getProduct().getName(),
                    item.getQuantity(),
                    item.getUnitPrice(),
                    item.getLineTotal());
        }
    }

    public static OrderResponse from(Order order) {
        List<Item> items = order.getItems().stream().map(Item::from).toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerName(),
                order.getStatus(),
                order.getTotalAmount(),
                order.getCreatedAt(),
                items);
    }
}
