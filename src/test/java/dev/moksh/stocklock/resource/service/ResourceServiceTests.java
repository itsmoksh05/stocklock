package dev.moksh.stocklock.resource.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.dto.CreateResourceRequest;
import dev.moksh.stocklock.resource.dto.ResourceResponse;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.junit.jupiter.api.Test;

class ResourceServiceTests {

	private final ResourceRepository resourceRepository = mock(ResourceRepository.class);
	private final ResourceService resourceService = new ResourceService(resourceRepository);

	@Test
	void createsAndPersistsResource() {
		when(resourceRepository.save(org.mockito.ArgumentMatchers.any(Resource.class)))
				.thenAnswer(invocation -> invocation.getArgument(0));

		ResourceResponse created = resourceService.create(new CreateResourceRequest("Laptop", "Loaner device", 2));

		assertThat(created.name()).isEqualTo("Laptop");
		assertThat(created.description()).isEqualTo("Loaner device");
		assertThat(created.quantity()).isEqualTo(2);
		verify(resourceRepository).save(org.mockito.ArgumentMatchers.argThat(resource ->
				resource.getName().equals("Laptop")
						&& resource.getDescription().equals("Loaner device")
						&& resource.getQuantity() == 2));
	}
}
