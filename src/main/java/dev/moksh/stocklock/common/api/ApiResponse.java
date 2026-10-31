package dev.moksh.stocklock.common.api;

import dev.moksh.stocklock.exception.ApiErrorResponse;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "Common API envelope. Successful responses contain data; error responses contain error.")
public record ApiResponse<T>(
		@Schema(description = "True for a successful operation and false for an API error.", example = "true") boolean success,
		@Schema(description = "Concrete resource or reservation response DTO for the operation.", nullable = true) T data,
		@Schema(description = "Present for error responses; null for successful responses.", nullable = true)
		ApiErrorResponse error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<ApiErrorResponse> error(ApiErrorResponse error) {
		return new ApiResponse<>(false, null, error);
	}
}
