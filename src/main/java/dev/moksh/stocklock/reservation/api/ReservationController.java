package dev.moksh.stocklock.reservation.api;

import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reservations")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ReservationResponse create(
			@Valid @RequestBody CreateReservationRequest request,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey) {
		return reservationService.create(request, idempotencyKey);
	}
}
