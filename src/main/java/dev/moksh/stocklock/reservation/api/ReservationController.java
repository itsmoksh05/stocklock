package dev.moksh.stocklock.reservation.api;

import dev.moksh.stocklock.common.api.ApiResponse;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationDetailsResponse;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.service.ReservationService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
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
	public ApiResponse<ReservationResponse> create(
			@Valid @RequestBody CreateReservationRequest request,
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey) {
		return ApiResponse.success(reservationService.create(request, idempotencyKey));
	}

	@GetMapping("/{id}")
	public ApiResponse<ReservationDetailsResponse> getById(@PathVariable("id") Long id) {
		return ApiResponse.success(reservationService.getById(id));
	}

	@DeleteMapping("/{id}")
	public ApiResponse<ReservationDetailsResponse> cancel(@PathVariable("id") Long id) {
		return ApiResponse.success(reservationService.cancel(id));
	}
}
