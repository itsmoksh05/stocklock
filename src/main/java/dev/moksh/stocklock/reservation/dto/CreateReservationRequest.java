package dev.moksh.stocklock.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(name = "CreateReservationRequest", description = "Fields needed to reserve currently available resource quantity.")
public record CreateReservationRequest(
		@Schema(description = "Identifier of the resource to reserve.", example = "1", minimum = "1")
		@NotNull @Positive Long resourceId,
		@Schema(description = "Positive quantity to reserve.", example = "3", minimum = "1")
		@NotNull @Positive Integer quantity) {
}
