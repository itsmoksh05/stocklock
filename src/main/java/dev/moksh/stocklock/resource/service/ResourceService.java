package dev.moksh.stocklock.resource.service;

import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceAvailabilityResponse;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ResourceService {
	private static final Logger logger = LoggerFactory.getLogger(ResourceService.class);

	private final ResourceRepository resourceRepository;

	public ResourceService(ResourceRepository resourceRepository) {
		this.resourceRepository = resourceRepository;
	}

	public ResourceResponse create(CreateResourceRequest request) {
		Resource resource = new Resource(request.name(), request.description(), request.quantity());
		Resource savedResource = resourceRepository.save(resource);
		logger.info("Created resource id={} quantity={}", savedResource.getId(), savedResource.getQuantity());
		return toResponse(savedResource);
	}

	public ResourceResponse getById(Long id) {
		return toResponse(findResource(id));
	}

	public ResourceAvailabilityResponse getAvailability(Long id) {
		Resource resource = findResource(id);
		return new ResourceAvailabilityResponse(resource.getId(), resource.getQuantity());
	}

	private Resource findResource(Long id) {
		return resourceRepository.findById(id)
				.orElseThrow(() -> {
					logger.warn("Resource not found id={}", id);
					return new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
				});
	}

	private ResourceResponse toResponse(Resource resource) {
		return new ResourceResponse(resource.getId(), resource.getName(), resource.getDescription(), resource.getQuantity());
	}
}
