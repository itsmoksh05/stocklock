package dev.moksh.stocklock.reservation.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Summary of a newly created or idempotently replayed reservation.")
public record ReservationResponse(
		@Schema(description = "Reservation identifier.", example = "101") Long id,
		@Schema(description = "Identifier of the reserved resource.", example = "1") Long resourceId,
		@Schema(description = "Quantity reserved.", example = "3", minimum = "1") int quantity) {
}
