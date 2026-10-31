package dev.moksh.stocklock.resource.service;

import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.springframework.stereotype.Service;

@Service
public class ResourceService {

	private final ResourceRepository resourceRepository;

	public ResourceService(ResourceRepository resourceRepository) {
		this.resourceRepository = resourceRepository;
	}

	public ResourceResponse create(CreateResourceRequest request) {
		Resource resource = new Resource(request.name(), request.description(), request.quantity());
		Resource savedResource = resourceRepository.save(resource);
		return new ResourceResponse(savedResource.getId(), savedResource.getName(),
				savedResource.getDescription(), savedResource.getQuantity());
	}
}
