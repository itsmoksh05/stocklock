package dev.moksh.stocklock.resource.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateResourceRequest", description = "Resource creation fields. Quantity is the initial available quantity and may be zero.")
public record CreateResourceRequest(
		@Schema(description = "Required resource name.", example = "Conference Room", minLength = 1, maxLength = 120)
		@NotBlank @Size(min = 1, max = 120) String name,
		@Schema(description = "Optional resource description.", example = "Meeting room on floor 2", maxLength = 500)
		@Size(max = 500) String description,
		@Schema(description = "Initial available quantity; zero is allowed.", example = "10", minimum = "0")
		@NotNull @PositiveOrZero Integer quantity) {
}
