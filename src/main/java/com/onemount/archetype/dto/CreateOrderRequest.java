package com.onemount.archetype.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Inbound payload to create an order synchronously. */
public record CreateOrderRequest(
        @NotBlank @Size(max = 200) String customerName,
        @NotEmpty @Valid List<Item> items) {

    /** One requested line: a product and a quantity. Unit price comes from the product. */
    public record Item(
            @NotNull UUID productId,
            @Positive int quantity) {
    }
}
