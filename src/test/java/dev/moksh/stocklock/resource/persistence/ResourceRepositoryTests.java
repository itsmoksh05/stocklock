package dev.moksh.stocklock.resource.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.moksh.stocklock.resource.domain.Resource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ResourceRepositoryTests {

	@Autowired
	private ResourceRepository resourceRepository;

	@Test
	void persistsAndLoadsResource() {
		Resource saved = resourceRepository.saveAndFlush(new Resource("Laptop", "Loaner device", 2));

		Resource loaded = resourceRepository.findById(saved.getId()).orElseThrow();
		assertThat(loaded.getName()).isEqualTo("Laptop");
		assertThat(loaded.getDescription()).isEqualTo("Loaner device");
		assertThat(loaded.getQuantity()).isEqualTo(2);
	}

	@Test
	void databaseRejectsNegativeQuantity() {
		assertThrows(DataIntegrityViolationException.class,
				() -> resourceRepository.saveAndFlush(new Resource("Invalid", null, -1)));
	}
}
