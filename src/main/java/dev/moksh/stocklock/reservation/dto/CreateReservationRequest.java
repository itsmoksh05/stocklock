package dev.moksh.stocklock.reservation.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record CreateReservationRequest(
		@NotNull @Positive Long resourceId,
		@NotNull @Positive Integer quantity) {
}
