package dev.moksh.stocklock.reservation.service;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.domain.ReservationStatus;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationDetailsResponse;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {
	private static final Logger logger = LoggerFactory.getLogger(ReservationService.class);

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

	public ReservationDetailsResponse getById(Long id) {
		Reservation reservation = reservationRepository.findById(id)
				.orElseThrow(() -> {
					logger.warn("Reservation not found id={}", id);
					return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
				});
		return toDetailsResponse(reservation);
	}

	public ReservationDetailsResponse cancel(Long id) {
		return transactionTemplate.execute(status -> cancelInTransaction(id));
	}

	private ReservationDetailsResponse cancelInTransaction(Long id) {
		Reservation reservation = reservationRepository.findById(id)
				.orElseThrow(() -> {
					logger.warn("Reservation not found for cancellation id={}", id);
					return new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found");
				});

		int updatedRows = reservationRepository.cancelIfActive(
				id, ReservationStatus.ACTIVE, ReservationStatus.CANCELLED);
		switch (updatedRows) {
			case 1 -> {
				int restoredRows = resourceRepository.incrementAvailableQuantity(
						reservation.getResourceId(), reservation.getQuantity());
				if (restoredRows != 1) {
					throw new IllegalStateException("Could not restore resource availability for reservation " + id);
				}
				logger.info("Cancelled reservation id={} resourceId={} quantity={}", id,
						reservation.getResourceId(), reservation.getQuantity());
			}
			case 0 -> {
				reservation = reservationRepository.findById(id)
						.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
				if (reservation.getStatus() == ReservationStatus.CANCELLED) {
					logger.debug("Reservation already cancelled id={}", id);
					return toDetailsResponse(reservation);
				}
				throw new IllegalStateException("Reservation status could not be updated");
			}
			default -> throw new IllegalStateException("Unexpected reservation update count: " + updatedRows);
		}

		Reservation updatedReservation = reservationRepository.findById(id)
				.orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Reservation not found"));
		return toDetailsResponse(updatedReservation);
	}

	private ReservationResponse createInTransaction(CreateReservationRequest request, String idempotencyKey) {
		Reservation existing = reservationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
		if (existing != null) {
			logger.debug("Replayed idempotent reservation id={}", existing.getId());
			return replayOrReject(existing, request);
		}

		int updatedRows = resourceRepository.decrementAvailableQuantity(request.resourceId(), request.quantity());
		if (updatedRows == 0) {
			Reservation concurrentReservation = reservationRepository.findByIdempotencyKey(idempotencyKey).orElse(null);
			if (concurrentReservation != null) {
				logger.debug("Replayed concurrent idempotent reservation id={}", concurrentReservation.getId());
				return replayOrReject(concurrentReservation, request);
			}
			if (!resourceRepository.existsById(request.resourceId())) {
				logger.warn("Reservation requested for missing resource id={}", request.resourceId());
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
			}
			logger.warn("Insufficient quantity for reservation resourceId={} requestedQuantity={}",
					request.resourceId(), request.quantity());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available quantity");
		}

		Reservation reservation = reservationRepository.saveAndFlush(
				new Reservation(request.resourceId(), request.quantity(), idempotencyKey));
		logger.info("Created reservation id={} resourceId={} quantity={}", reservation.getId(),
				reservation.getResourceId(), reservation.getQuantity());
		return toResponse(reservation);
	}

	private ReservationResponse replayOrReject(Reservation reservation, CreateReservationRequest request) {
		if (!reservation.getResourceId().equals(request.resourceId()) || reservation.getQuantity() != request.quantity()) {
			logger.warn("Idempotency request mismatch reservationId={} resourceId={} requestedResourceId={}",
					reservation.getId(), reservation.getResourceId(), request.resourceId());
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Idempotency key was already used with a different request");
		}
		return toResponse(reservation);
	}

	private ReservationResponse toResponse(Reservation reservation) {
		return new ReservationResponse(reservation.getId(), reservation.getResourceId(), reservation.getQuantity());
	}

	private ReservationDetailsResponse toDetailsResponse(Reservation reservation) {
		return new ReservationDetailsResponse(reservation.getId(), reservation.getResourceId(),
				reservation.getQuantity(), reservation.getStatus());
	}
}
