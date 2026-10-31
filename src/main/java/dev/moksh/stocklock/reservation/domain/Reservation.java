package dev.moksh.stocklock.reservation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "reservations")
public class Reservation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "resource_id", nullable = false)
	private Long resourceId;

	@Column(nullable = false)
	private int quantity;

	@Column(name = "idempotency_key", nullable = false, length = 255, unique = true)
	private String idempotencyKey;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 20)
	private ReservationStatus status = ReservationStatus.ACTIVE;

	protected Reservation() {
	}

	public Reservation(Long resourceId, int quantity, String idempotencyKey) {
		this.resourceId = resourceId;
		this.quantity = quantity;
		this.idempotencyKey = idempotencyKey;
	}

	public Long getId() {
		return id;
	}

	public Long getResourceId() {
		return resourceId;
	}

	public int getQuantity() {
		return quantity;
	}

	public String getIdempotencyKey() {
		return idempotencyKey;
	}

	public ReservationStatus getStatus() {
		return status;
	}
}
