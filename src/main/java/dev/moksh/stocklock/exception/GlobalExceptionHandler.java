package dev.moksh.stocklock.exception;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.MessageSourceResolvable;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.validation.method.ParameterErrors;
import org.springframework.validation.method.ParameterValidationResult;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

	private static final Logger logger = LoggerFactory.getLogger(GlobalExceptionHandler.class);
	private static final Set<String> SAFE_RESPONSE_STATUS_MESSAGES = Set.of(
			"Resource not found",
			"Reservation not found",
			"Insufficient available quantity",
			"Idempotency key was already used with a different request");

	@ExceptionHandler(MethodArgumentNotValidException.class)
	public ResponseEntity<ApiErrorResponse> handleBodyValidation(
			MethodArgumentNotValidException exception, HttpServletRequest request) {
		Map<String, String> fieldErrors = new TreeMap<>();
		for (FieldError fieldError : exception.getBindingResult().getFieldErrors()) {
			addFieldError(fieldErrors, fieldError.getField(), fieldError.getDefaultMessage());
		}
		return error(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(HandlerMethodValidationException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodValidation(
			HandlerMethodValidationException exception, HttpServletRequest request) {
		Map<String, String> fieldErrors = new TreeMap<>();
		for (ParameterValidationResult result : exception.getParameterValidationResults()) {
			if (result instanceof ParameterErrors parameterErrors) {
				for (FieldError fieldError : parameterErrors.getFieldErrors()) {
					addFieldError(fieldErrors, fieldError.getField(), fieldError.getDefaultMessage());
				}
			} else {
				String field = result.getMethodParameter().getParameterName();
				if (field == null || field.isBlank()) {
					field = "request";
				}
				String message = result.getResolvableErrors().stream()
						.map(error -> error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage())
						.sorted()
						.collect(Collectors.joining("; "));
				addFieldError(fieldErrors, field, message);
			}
		}
		for (MessageSourceResolvable error : exception.getCrossParameterValidationResults()) {
			String message = error.getDefaultMessage() == null ? "is invalid" : error.getDefaultMessage();
			addFieldError(fieldErrors, "request", message);
		}
		return error(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	public ResponseEntity<ApiErrorResponse> handleConstraintViolation(
			ConstraintViolationException exception, HttpServletRequest request) {
		Map<String, String> fieldErrors = new TreeMap<>();
		for (ConstraintViolation<?> violation : exception.getConstraintViolations()) {
			String field = violation.getPropertyPath().toString();
			for (jakarta.validation.Path.Node node : violation.getPropertyPath()) {
				field = node.getName();
			}
			addFieldError(fieldErrors, field, violation.getMessage());
		}
		return error(HttpStatus.BAD_REQUEST, "Request validation failed", request, fieldErrors);
	}

	@ExceptionHandler(MissingRequestHeaderException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingHeader(
			MissingRequestHeaderException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Required request header is missing", request);
	}

	@ExceptionHandler(HttpMessageNotReadableException.class)
	public ResponseEntity<ApiErrorResponse> handleUnreadableBody(
			HttpMessageNotReadableException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Invalid request body", request);
	}

	@ExceptionHandler(MissingServletRequestParameterException.class)
	public ResponseEntity<ApiErrorResponse> handleMissingRequestParameter(
			MissingServletRequestParameterException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Required request parameter is missing", request);
	}

	@ExceptionHandler(MethodArgumentTypeMismatchException.class)
	public ResponseEntity<ApiErrorResponse> handleTypeMismatch(
			MethodArgumentTypeMismatchException exception, HttpServletRequest request) {
		return error(HttpStatus.BAD_REQUEST, "Invalid request parameter", request);
	}

	@ExceptionHandler(HttpRequestMethodNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleMethodNotAllowed(
			HttpRequestMethodNotSupportedException exception, HttpServletRequest request) {
		return error(HttpStatus.METHOD_NOT_ALLOWED, "HTTP method not allowed", request);
	}

	@ExceptionHandler(HttpMediaTypeNotSupportedException.class)
	public ResponseEntity<ApiErrorResponse> handleUnsupportedMediaType(
			HttpMediaTypeNotSupportedException exception, HttpServletRequest request) {
		return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported media type", request);
	}

	@ExceptionHandler(NoResourceFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoResourceFound(
			NoResourceFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "Resource not found", request);
	}

	@ExceptionHandler(NoHandlerFoundException.class)
	public ResponseEntity<ApiErrorResponse> handleNoHandlerFound(
			NoHandlerFoundException exception, HttpServletRequest request) {
		return error(HttpStatus.NOT_FOUND, "Endpoint not found", request);
	}

	@ExceptionHandler(ResponseStatusException.class)
	public ResponseEntity<ApiErrorResponse> handleResponseStatus(
			ResponseStatusException exception, HttpServletRequest request) {
		HttpStatusCode status = exception.getStatusCode();
		String message = exception.getReason();
		if (message == null || !SAFE_RESPONSE_STATUS_MESSAGES.contains(message)) {
			message = switch (status.value()) {
				case 400 -> "Invalid request";
				case 404 -> "Resource not found";
				case 409 -> "Request conflicts with the current state";
				default -> "Request could not be completed";
			};
		}
		return error(status, message, request);
	}

	@ExceptionHandler(Exception.class)
	public ResponseEntity<ApiErrorResponse> handleUnexpectedException(Exception exception, HttpServletRequest request) {
		logger.error("Unhandled API exception for {} {}", request.getMethod(), request.getRequestURI(), exception);
		return error(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred.", request);
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatusCode status, String message, HttpServletRequest request) {
		return error(status, message, request, Map.of());
	}

	private ResponseEntity<ApiErrorResponse> error(HttpStatusCode status, String message,
			HttpServletRequest request, Map<String, String> fieldErrors) {
		HttpStatus resolvedStatus = HttpStatus.resolve(status.value());
		String reasonPhrase = resolvedStatus == null ? "Unknown Status" : resolvedStatus.getReasonPhrase();
		ApiErrorResponse body = new ApiErrorResponse(
				Instant.now(), status.value(), reasonPhrase, message, request.getRequestURI(), fieldErrors);
		return ResponseEntity.status(status).body(body);
	}

	private void addFieldError(Map<String, String> fieldErrors, String field, String message) {
		String safeField = field == null || field.isBlank() ? "request" : field;
		String safeMessage = message == null || message.isBlank() ? "is invalid" : message;
		fieldErrors.merge(safeField, safeMessage, (first, second) -> first.compareTo(second) <= 0
				? first + "; " + second : second + "; " + first);
	}
}
