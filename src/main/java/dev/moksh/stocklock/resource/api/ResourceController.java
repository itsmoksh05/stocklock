package dev.moksh.stocklock.resource.api;

import dev.moksh.stocklock.common.api.ApiResponse;
import dev.moksh.stocklock.common.openapi.ApiResponseSchemas;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceAvailabilityResponse;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.service.ResourceService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/resources")
@Tag(name = "Resources", description = "Create and inspect quantity-based reservable resources.")
public class ResourceController {

	private final ResourceService resourceService;

	public ResourceController(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	@Operation(summary = "Create a resource", description = "Creates a resource with its initial available quantity.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "201", description = "Resource created", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ResourceResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"id":1,"name":"Conference Room","description":"Meeting room on floor 2","quantity":10},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Request validation failed", content = @Content(
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":400,"error":"Bad Request","message":"Request validation failed","path":"/api/v1/resources","fieldErrors":{"name":"must not be blank"}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
		return ApiResponse.success(resourceService.create(request));
	}

	@GetMapping("/{id}")
	@Operation(summary = "Get resource details", description = "Returns the resource and its currently available quantity.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Resource details", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ResourceResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"id":1,"name":"Conference Room","description":"Meeting room on floor 2","quantity":10},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(
					schema = @Schema(implementation = ApiResponse.class), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":404,"error":"Not Found","message":"Resource not found","path":"/api/v1/resources/999","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid resource ID", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ResourceResponse> getById(@Parameter(description = "Resource identifier", example = "1") @PathVariable("id") Long id) {
		return ApiResponse.success(resourceService.getById(id));
	}

	@GetMapping("/{id}/availability")
	@Operation(summary = "Get resource availability", description = "Returns the current available quantity, which is the resource quantity remaining after reservations.")
	@ApiResponses({
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "200", description = "Current availability", content = @Content(
					schema = @Schema(implementation = ApiResponseSchemas.ResourceAvailabilityResponseEnvelope.class), examples = @ExampleObject(value = """
						{"success":true,"data":{"resourceId":1,"availableQuantity":10},"error":null}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "404", description = "Resource not found", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"), examples = @ExampleObject(value = """
						{"success":false,"data":null,"error":{"timestamp":"2026-01-01T12:00:00Z","status":404,"error":"Not Found","message":"Resource not found","path":"/api/v1/resources/999","fieldErrors":{}}}
						"""))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "400", description = "Invalid resource ID", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse"))),
			@io.swagger.v3.oas.annotations.responses.ApiResponse(responseCode = "500", description = "Unexpected server error", content = @Content(
					schema = @Schema(ref = "#/components/schemas/ApiResponse")))
	})
	public ApiResponse<ResourceAvailabilityResponse> getAvailability(@Parameter(description = "Resource identifier", example = "1") @PathVariable("id") Long id) {
		return ApiResponse.success(resourceService.getAvailability(id));
	}
}
