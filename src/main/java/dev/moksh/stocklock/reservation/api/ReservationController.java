package dev.moksh.stocklock.reservation.api;

import dev.moksh.stocklock.common.api.ApiResponse;
import dev.moksh.stocklock.common.openapi.ApiResponseSchemas;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationDetailsResponse;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.service.ReservationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
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
@RequestMapping("/api/v1/reservations")
@Tag(name = "Reservations", description = "Create, inspect, and cancel quantity reservations.")
public class ReservationController {

	private final ReservationService reservationService;

	public ReservationController(ReservationService reservationService) {
		this.reservationService = reservationService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a reservation", description = "Reserves the requested quantity if available. The Idempotency-Key makes equivalent retries return the original reservation without decrementing availability again.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Reservation created or equivalent idempotent request replayed", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ReservationResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"id":101,"resourceId":1,"quantity":3},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid request or missing/invalid Idempotency-Key", content = @Content(
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":400,"error":"Bad Request","message":"Request validation failed","path":"/api/v1/reservations","fieldErrors":{"quantity":"must be greater than 0"}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":404,"error":"Not Found","message":"Resource not found","path":"/api/v1/reservations","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "409", description = "Insufficient availability or idempotency key reused with different request parameters", content = @Content(
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":409,"error":"Conflict","message":"Insufficient available quantity","path":"/api/v1/reservations","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ReservationResponse> create(
			@Valid @RequestBody CreateReservationRequest request,
			@Parameter(name = "Idempotency-Key", description = "Required key used to safely retry an equivalent reservation request.", required = true, in = ParameterIn.HEADER, example = "client-request-abc123")
			@RequestHeader("Idempotency-Key") @NotBlank @Size(max = 255) String idempotencyKey) {
		return ApiResponse.success(reservationService.create(request, idempotencyKey));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get reservation details", description = "Returns a reservation and its current lifecycle status.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reservation details", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ReservationDetailsResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"id":101,"resourceId":1,"quantity":3,"status":"ACTIVE"},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Reservation not found", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":404,"error":"Not Found","message":"Reservation not found","path":"/api/v1/reservations/999","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid reservation ID", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ReservationDetailsResponse> getById(@Parameter(description = "Reservation identifier", example = "101") @PathVariable("id") Long id) {
		return ApiResponse.success(reservationService.getById(id));
	}

	@DeleteMapping("/{id}")
	@Operation(summary = "Cancel a reservation", description = "Cancels an active reservation and restores its reserved quantity. Repeated cancellation is safe and does not restore the quantity again.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Reservation cancelled or already cancelled", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ReservationDetailsResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"id":101,"resourceId":1,"quantity":3,"status":"CANCELLED"},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Reservation not found", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":404,"error":"Not Found","message":"Reservation not found","path":"/api/v1/reservations/999","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid reservation ID", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ReservationDetailsResponse> cancel(@Parameter(description = "Reservation identifier", example = "101") @PathVariable("id") Long id) {
		return ApiResponse.success(reservationService.cancel(id));
	}
}
