package dev.moksh.stocklock.resource.api;

import dev.moksh.stocklock.common.api.ApiResponse;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceAvailabilityResponse;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.service.ResourceService;
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
@RequestMapping("/api/resources")
public class ResourceController {

	private final ResourceService resourceService;

	public ResourceController(ResourceService resourceService) {
		this.resourceService = resourceService;
	}

	@PostMapping
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<ResourceResponse> create(@Valid @RequestBody CreateResourceRequest request) {
		return ApiResponse.success(resourceService.create(request));
	}

	@GetMapping("/{id}")
	public ApiResponse<ResourceResponse> getById(@PathVariable("id") Long id) {
		return ApiResponse.success(resourceService.getById(id));
	}

	@GetMapping("/{id}/availability")
	public ApiResponse<ResourceAvailabilityResponse> getAvailability(@PathVariable("id") Long id) {
		return ApiResponse.success(resourceService.getAvailability(id));
	}
}
