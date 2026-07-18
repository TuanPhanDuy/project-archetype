package com.onemount.archetype.dto;

import com.onemount.archetype.domain.Category;
import com.onemount.archetype.domain.Product;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Outbound representation of a product, including a light reference to its category.
 * Decoupling this from the entity lets the schema evolve without leaking persistence
 * details onto the wire.
 */
public record ProductResponse(
        UUID id,
        String name,
        @Nullable String description,
        BigDecimal price,
        @Nullable UUID categoryId,
        @Nullable String categoryName,
        Instant createdAt) {

    public static ProductResponse from(Product product) {
        Category category = product.getCategory();
        return new ProductResponse(
                product.getId(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                category != null ? category.getId() : null,
                category != null ? category.getName() : null,
                product.getCreatedAt());
    }
}
