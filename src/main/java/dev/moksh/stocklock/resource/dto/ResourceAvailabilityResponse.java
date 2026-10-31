package dev.moksh.stocklock.resource.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "The current available quantity for a resource.")
public record ResourceAvailabilityResponse(
		@Schema(description = "Resource identifier.", example = "1") Long resourceId,
		@Schema(description = "Quantity currently available to reserve.", example = "10", minimum = "0") int availableQuantity) {
}
