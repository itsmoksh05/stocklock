package dev.moksh.stocklock.common.openapi;

import dev.moksh.stocklock.exception.ApiErrorResponse;
import dev.moksh.stocklock.reservation.dto.ReservationDetailsResponse;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.resource.dto.ResourceAvailabilityResponse;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import io.swagger.v3.oas.annotations.media.Schema;

/** Typed schema views used to describe ApiResponse<T> for each endpoint in OpenAPI. */
public final class ApiResponseSchemas {

	private ApiResponseSchemas() {
	}

	@Schema(name = "ApiResponseResourceResponse", description = "Success envelope containing resource details.")
	public record ResourceResponseEnvelope(boolean success, ResourceResponse data, ApiErrorResponse error) {
	}

	@Schema(name = "ApiResponseResourceAvailabilityResponse", description = "Success envelope containing current resource availability.")
	public record ResourceAvailabilityResponseEnvelope(
			boolean success, ResourceAvailabilityResponse data, ApiErrorResponse error) {
	}

	@Schema(name = "ApiResponseReservationResponse", description = "Success envelope containing a reservation summary.")
	public record ReservationResponseEnvelope(boolean success, ReservationResponse data, ApiErrorResponse error) {
	}

	@Schema(name = "ApiResponseReservationDetailsResponse", description = "Success envelope containing reservation details.")
	public record ReservationDetailsResponseEnvelope(
			boolean success, ReservationDetailsResponse data, ApiErrorResponse error) {
	}
}
