package dev.moksh.stocklock.reservation.persistence;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, Long> {
	Optional<Reservation> findByIdempotencyKey(String idempotencyKey);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update Reservation reservation set reservation.status = :cancelled "
			+ "where reservation.id = :reservationId and reservation.status = :active")
	int cancelIfActive(@Param("reservationId") Long reservationId,
			@Param("active") ReservationStatus active,
			@Param("cancelled") ReservationStatus cancelled);
}
