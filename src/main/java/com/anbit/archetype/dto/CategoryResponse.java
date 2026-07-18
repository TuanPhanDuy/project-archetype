package com.anbit.archetype.dto;

import com.anbit.archetype.domain.Category;
import java.time.Instant;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

public record CategoryResponse(
        UUID id,
        String name,
        @Nullable String description,
        Instant createdAt) {

    public static CategoryResponse from(Category category) {
        return new CategoryResponse(
                category.getId(),
                category.getName(),
                category.getDescription(),
                category.getCreatedAt());
    }
}
