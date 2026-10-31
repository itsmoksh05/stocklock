package dev.moksh.stocklock.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record CreateResourceRequest(
		@NotBlank @Size(max = 120) String name,
		@Size(max = 500) String description,
		@NotNull @PositiveOrZero Integer quantity) {
}
