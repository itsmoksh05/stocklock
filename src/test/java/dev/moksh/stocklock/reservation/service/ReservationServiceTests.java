package dev.moksh.stocklock.reservation.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.domain.ReservationStatus;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationDetailsResponse;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;

@SpringBootTest
@ActiveProfiles("test")
class ReservationServiceTests {

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private ReservationService reservationService;

	@Test
	void createsReservationAndPersistsQuantityDecrement() {
		Resource resource = resource("Create", 8);
		long reservationCountBefore = reservationRepository.count();
		String key = key();

		ReservationResponse response = reservationService.create(request(resource, 3), key);

		assertThat(response.id()).isNotNull();
		assertThat(response.resourceId()).isEqualTo(resource.getId());
		assertThat(response.quantity()).isEqualTo(3);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(5);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
		assertThat(reservationRepository.findByIdempotencyKey(key)).get()
				.satisfies(saved -> {
					assertThat(saved.getId()).isEqualTo(response.id());
					assertThat(saved.getResourceId()).isEqualTo(resource.getId());
					assertThat(saved.getQuantity()).isEqualTo(3);
				});
	}

	@Test
	void missingResourceReturnsNotFoundWithoutPersistingReservation() {
		String key = key();
		long reservationCountBefore = reservationRepository.count();

		assertThatThrownBy(() -> reservationService.create(new CreateReservationRequest(Long.MAX_VALUE, 1), key))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);

		assertThat(reservationRepository.findByIdempotencyKey(key)).isEmpty();
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore);
	}

	@Test
	void insufficientAvailabilityReturnsConflictWithoutChangingDatabaseState() {
		Resource resource = resource("Insufficient", 2);
		String key = key();
		long reservationCountBefore = reservationRepository.count();

		assertThatThrownBy(() -> reservationService.create(request(resource, 3), key))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(2);
		assertThat(reservationRepository.findByIdempotencyKey(key)).isEmpty();
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore);
	}

	@Test
	void equivalentIdempotentReplayReturnsOriginalWithoutChangingDatabaseAgain() {
		Resource resource = resource("Replay", 9);
		String key = key();
		ReservationResponse original = reservationService.create(request(resource, 4), key);
		long reservationCountAfterCreate = reservationRepository.count();

		ReservationResponse replay = reservationService.create(request(resource, 4), key);

		assertThat(replay).isEqualTo(original);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(5);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountAfterCreate);
	}

	@Test
	void sameIdempotencyKeyWithDifferentResourceReturnsConflict() {
		Resource originalResource = resource("Original", 8);
		Resource otherResource = resource("Other", 8);
		String key = key();
		reservationService.create(request(originalResource, 2), key);

		assertThatThrownBy(() -> reservationService.create(request(otherResource, 2), key))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);

		assertThat(resourceRepository.findById(originalResource.getId()).orElseThrow().getQuantity()).isEqualTo(6);
		assertThat(resourceRepository.findById(otherResource.getId()).orElseThrow().getQuantity()).isEqualTo(8);
	}

	@Test
	void sameIdempotencyKeyWithDifferentQuantityReturnsConflict() {
		Resource resource = resource("Quantity mismatch", 8);
		String key = key();
		reservationService.create(request(resource, 2), key);
		long reservationCountAfterCreate = reservationRepository.count();

		assertThatThrownBy(() -> reservationService.create(request(resource, 3), key))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.CONFLICT);

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(6);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountAfterCreate);
	}

	@Test
	void differentIdempotencyKeysPersistDistinctReservationsForEquivalentRequests() {
		Resource resource = resource("Distinct keys", 8);

		ReservationResponse first = reservationService.create(request(resource, 2), key());
		ReservationResponse second = reservationService.create(request(resource, 2), key());

		assertThat(first.id()).isNotEqualTo(second.id());
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(4);
		assertThat(reservationRepository.findById(first.id())).isPresent();
		assertThat(reservationRepository.findById(second.id())).isPresent();
	}

	@Test
	void retrievesReservationDetailsIncludingActiveStatus() {
		Resource resource = resource("Retrieve", 6);
		ReservationResponse created = reservationService.create(request(resource, 3), key());

		ReservationDetailsResponse details = reservationService.getById(created.id());

		assertThat(details).isEqualTo(new ReservationDetailsResponse(
				created.id(), resource.getId(), 3, ReservationStatus.ACTIVE));
	}

	@Test
	void retrievingMissingReservationReturnsNotFound() {
		assertThatThrownBy(() -> reservationService.getById(Long.MAX_VALUE))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	@Test
	void cancellationRestoresQuantityAndPersistsCancelledReservation() {
		Resource resource = resource("Cancel", 7);
		ReservationResponse created = reservationService.create(request(resource, 3), key());
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(4);

		ReservationDetailsResponse cancelled = reservationService.cancel(created.id());

		assertThat(cancelled.status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.findById(created.id())).get()
				.extracting(Reservation::getStatus).isEqualTo(ReservationStatus.CANCELLED);
	}

	@Test
	void repeatedCancellationRestoresQuantityOnlyOnce() {
		Resource resource = resource("Repeated cancel", 7);
		ReservationResponse created = reservationService.create(request(resource, 3), key());

		reservationService.cancel(created.id());
		ReservationDetailsResponse secondCancellation = reservationService.cancel(created.id());

		assertThat(secondCancellation.status()).isEqualTo(ReservationStatus.CANCELLED);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.findById(created.id())).isPresent();
	}

	@Test
	void cancellingMissingReservationReturnsNotFound() {
		assertThatThrownBy(() -> reservationService.cancel(Long.MAX_VALUE))
				.isInstanceOf(ResponseStatusException.class)
				.extracting(exception -> ((ResponseStatusException) exception).getStatusCode())
				.isEqualTo(HttpStatus.NOT_FOUND);
	}

	private Resource resource(String name, int quantity) {
		return resourceRepository.saveAndFlush(new Resource(name, "SL-09 service test", quantity));
	}

	private CreateReservationRequest request(Resource resource, int quantity) {
		return new CreateReservationRequest(resource.getId(), quantity);
	}

	private String key() {
		return "sl-09-" + UUID.randomUUID();
	}
}
