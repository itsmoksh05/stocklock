package dev.moksh.stocklock.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTransactionTests {

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationService reservationService;

	@MockitoBean
	private ReservationRepository reservationRepository;

	@Test
	void rollsBackQuantityDecrementWhenReservationPersistenceFails() {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Projector", null, 5));
		when(reservationRepository.save(any(Reservation.class)))
				.thenThrow(new IllegalStateException("Simulated reservation persistence failure"));

		assertThatThrownBy(() -> reservationService.create(new CreateReservationRequest(resource.getId(), 2)))
				.isInstanceOf(IllegalStateException.class);

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(5);
	}
}
