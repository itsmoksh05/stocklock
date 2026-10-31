package dev.moksh.stocklock.reservation.service;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;
	private final TransactionTemplate transactionTemplate;

	public ReservationService(ResourceRepository resourceRepository, ReservationRepository reservationRepository,
			PlatformTransactionManager transactionManager) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	public ReservationResponse create(CreateReservationRequest request, String idempotencyKey) {
		try {
			return transactionTemplate.execute(status -> createInTransaction(request, idempotencyKey));
		} catch (DataIntegrityViolationException exception) {
			return reservationRepository.findByIdempotencyKey(idempotencyKey)
					.map(reservation -> replayOrReject(reservation, request))
					.orElseThrow(() -> exception);
		}
	}

	private ReservationResponse createInTransaction(CreateReservationRequest request, String idempotencyKey) {
		Reservation existing = reservationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			return replayOrReject(existing, request);
		}

		int updatedRows = resourceRepository.decrementAvailableQuantity(request.resourceId(), request.quantity());
		if (updatedRows == 0) {
			Reservation concurrentReservation = reservationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
			if (concurrentReservation != null) {
				return replayOrReject(concurrentReservation, request);
			}
			if (!resourceRepository.existsById(request.resourceId())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
			}
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available quantity");
		}

		Reservation reservation = reservationRepository.saveAndFlush(
				new Reservation(request.resourceId(), request.quantity(), idempotencyKey));
		return toResponse(reservation);
	}

	private ReservationResponse replayOrReject(Reservation reservation, CreateReservationRequest request) {
		if (!reservation.getResourceId().equals(request.resourceId()) || reservation.getQuantity() != request.quantity()) {
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used with a different request");
		}
		return toResponse(reservation);
	}

	private ReservationResponse toResponse(Reservation reservation) {
		return new ReservationResponse(reservation.getId(), reservation.getResourceId(), reservation.getQuantity());
	}
}
