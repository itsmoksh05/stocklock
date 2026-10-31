package dev.moksh.stocklock.reservation.domain;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Reservation lifecycle status.")
public enum ReservationStatus {
	ACTIVE,
	CANCELLED
}
