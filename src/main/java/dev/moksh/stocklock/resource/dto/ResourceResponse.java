package dev.moksh.stocklock.resource.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Resource details, including the current available quantity.")
public record ResourceResponse(
		@Schema(description = "Resource identifier.", example = "1") Long id,
		@Schema(description = "Resource name.", example = "Conference Room") String name,
		@Schema(description = "Optional resource description.", example = "Meeting room on floor 2") String description,
		@Schema(description = "Current available quantity.", example = "10", minimum = "0") int quantity) {
}
