package dev.moksh.stocklock.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doReturn;

import dev.moksh.stocklock.reservation.domain.ReservationStatus;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

@SpringBootTest
@ActiveProfiles("test")
class ReservationCancellationTransactionTests {

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ReservationService reservationService;

	@MockitoSpyBean
	private ResourceRepository resourceRepositorySpy;

	@Test
	void rollsBackCancellationWhenResourceRestorationUpdatesNoRows() {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Projector", null, 5));
		long reservationId = reservationService.create(
				new CreateReservationRequest(resource.getId(), 2), "cancel-rollback-key").id();
		doReturn(0).when(resourceRepositorySpy).incrementAvailableQuantity(resource.getId(), 2);

		assertThatThrownBy(() -> reservationService.cancel(reservationId))
				.isInstanceOf(IllegalStateException.class);

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(3);
		assertThat(reservationRepository.findById(reservationId).orElseThrow().getStatus())
				.isEqualTo(ReservationStatus.ACTIVE);
	}
}
