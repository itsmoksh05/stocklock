package dev.moksh.stocklock.exception;

import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.TreeMap;

public record ApiErrorResponse(
		Instant timestamp,
		int status,
		String error,
		String message,
		String path,
		Map<String, String> fieldErrors) {

	public ApiErrorResponse {
		fieldErrors = Collections.unmodifiableMap(new TreeMap<>(fieldErrors));
	}
}
