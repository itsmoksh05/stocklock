package dev.moksh.stocklock.reservation.dto;

import dev.moksh.stocklock.reservation.domain.ReservationStatus;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reservation details and lifecycle status.")
public record ReservationDetailsResponse(
		@Schema(description = "Reservation identifier.", example = "101") Long id,
		@Schema(description = "Identifier of the reserved resource.", example = "1") Long resourceId,
		@Schema(description = "Quantity reserved.", example = "3", minimum = "1") int quantity,
		@Schema(description = "ACTIVE while reserved; CANCELLED after availability has been restored.", example = "ACTIVE")
		ReservationStatus status) {
}
