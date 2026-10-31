package dev.moksh.stocklock.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Safe error details. Field errors are empty when the failure is not field validation.", nullable = true)
public record ApiErrorResponse(
		@Schema(description = "Time the error was produced in UTC.", example = "2026-01-01T12:00:00Z") Instant timestamp,
		@Schema(description = "HTTP status code.", example = "400") int status,
		@Schema(description = "HTTP reason phrase.", example = "Bad Request") String error,
		@Schema(description = "Safe application-facing error message.", example = "Request validation failed") String message,
		@Schema(description = "Request URI that produced the error.", example = "/api/v1/resources") String path,
		@Schema(description = "Validation errors keyed by request field; empty for non-validation errors.") Map<String, String> fieldErrors) {

	public ApiErrorResponse {
		fieldErrors = Collections.unmodifiableMap(new TreeMap<>(fieldErrors));
	}
}
