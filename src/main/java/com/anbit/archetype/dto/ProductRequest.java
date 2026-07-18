package com.anbit.archetype.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.UUID;
import org.jspecify.annotations.Nullable;

/**
 * Inbound payload for creating/updating a product. Validation runs at the controller
 * boundary; the entity never trusts unvalidated input. {@code categoryId} is optional —
 * when present it must reference an existing category.
 */
public record ProductRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) @Nullable String description,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal price,
        @Nullable UUID categoryId) {
}
