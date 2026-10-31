package dev.moksh.stocklock.common.api;

import dev.moksh.stocklock.exception.ApiErrorResponse;

public record ApiResponse<T>(boolean success, T data, ApiErrorResponse error) {

	public static <T> ApiResponse<T> success(T data) {
		return new ApiResponse<>(true, data, null);
	}

	public static ApiResponse<ApiErrorResponse> error(ApiErrorResponse error) {
		return new ApiResponse<>(false, null, error);
	}
}
