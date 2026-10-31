package dev.moksh.stocklock.reservation.service;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class ReservationService {

	private final ResourceRepository resourceRepository;
	private final ReservationRepository reservationRepository;

	public ReservationService(ResourceRepository resourceRepository, ReservationRepository reservationRepository) {
		this.resourceRepository = resourceRepository;
		this.reservationRepository = reservationRepository;
	}

	@Transactional
	public ReservationResponse create(CreateReservationRequest request) {
		int updatedRows = resourceRepository.decrementAvailableQuantity(request.resourceId(), request.quantity());
		if (updatedRows == 0) {
			if (!resourceRepository.existsById(request.resourceId())) {
				throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Resource not found");
			}
			throw new ResponseStatusException(HttpStatus.CONFLICT, "Insufficient available quantity");
		}

		Reservation reservation = reservationRepository.save(
				new Reservation(request.resourceId(), request.quantity()));
		return new ReservationResponse(reservation.getId(), reservation.getResourceId(), reservation.getQuantity());
	}
}
