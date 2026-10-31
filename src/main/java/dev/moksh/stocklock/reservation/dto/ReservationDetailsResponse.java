package dev.moksh.stocklock.reservation.dto;

import dev.moksh.stocklock.reservation.domain.ReservationStatus;

public record ReservationDetailsResponse(Long id, Long resourceId, int quantity, ReservationStatus status) {
}
