package dev.moksh.stocklock.reservation.persistence;

import static org.junit.jupiter.api.Assertions.assertThrows;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;

@DataJpaTest
@ActiveProfiles("test")
class ReservationRepositoryTests {

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ResourceRepository resourceRepository;

	@Test
	void databaseRejectsDuplicateIdempotencyKeys() {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 5));
		reservationRepository.saveAndFlush(new Reservation(resource.getId(), 1, "duplicate-key"));

		Reservation reservation = new Reservation(resource.getId(), 1, "duplicate-key");
		assertThrows(DataIntegrityViolationException.class,
				() -> reservationRepository.saveAndFlush(reservation));
	}
}
