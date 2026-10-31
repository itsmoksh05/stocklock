package dev.moksh.stocklock.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceAvailabilityResponse;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

class ResourceServiceTests {

	private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
	private final ResourceService resourceService = new ResourceService(resourceRepository);

	@Test
	void createsAndPersistsResource() {
		Resource persistedResource = resource(10L, "Laptop", "Loaner device", 2);
		when(resourceRepository.save(any(Resource.class))).thenReturn(persistedResource);

		ResourceResponse created = resourceService.create(new CreateResourceRequest("Laptop", "Loaner device", 2));

		assertThat(created).isEqualTo(new ResourceResponse(10L, "Laptop", "Loaner device", 2));
		verify(resourceRepository).save(argThat(resource -> resource.getName().equals("Laptop")
				&& resource.getDescription().equals("Loaner device") && resource.getQuantity() == 2));
	}

	@Test
	void retrievesResourceAndMapsResponse() {
		when(resourceRepository.findById(11L)).thenReturn(Optional.of(resource(11L, "Room", "Floor 2", 4)));

		ResourceResponse response = resourceService.getById(11L);

		assertThat(response).isEqualTo(new ResourceResponse(11L, "Room", "Floor 2", 4));
		verify(resourceRepository).findById(11L);
	}

	@Test
	void mapsCurrentAvailableQuantity() {
		when(resourceRepository.findById(12L)).thenReturn(Optional.of(resource(12L, "Desk", null, 3)));

		ResourceAvailabilityResponse response = resourceService.getAvailability(12L);

		assertThat(response).isEqualTo(new ResourceAvailabilityResponse(12L, 3));
		verify(resourceRepository).findById(12L);
	}

	@Test
	void retrievingMissingResourceReturnsNotFound() {
		when(resourceRepository.findById(13L)).thenReturn(Optional.empty());

		assertNotFound(() -> resourceService.getById(13L));
		verify(resourceRepository).findById(13L);
	}

	@Test
	void checkingAvailabilityForMissingResourceReturnsNotFound() {
		when(resourceRepository.findById(14L)).thenReturn(Optional.empty());

		assertNotFound(() -> resourceService.getAvailability(14L));
		verify(resourceRepository).findById(14L);
	}

	private void assertNotFound(Runnable operation) {
		assertThatThrownBy(operation::run)
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode().value())
				.isEqualTo(404);
	}

	private Resource resource(long id, String name, String description, int quantity) {
		Resource resource = new Resource(name, description, quantity);
		ReflectionTestUtils.setField(resource, "id", id);
		return resource;
	}
}
