package com.onemount.archetype.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

public record CategoryRequest(
        @NotBlank @Size(max = 200) String name,
        @Size(max = 2000) @Nullable String description) {
}
