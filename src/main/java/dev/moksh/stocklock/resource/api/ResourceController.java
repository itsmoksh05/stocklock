package dev.moksh.stocklock.resource.api;

import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.service.ResourceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
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
	public ResourceResponse create(@Valid @RequestBody CreateResourceRequest request) {
		return resourceService.create(request);
	}
}
