package dev.moksh.stocklock.reservation.concurrency;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.moksh.stocklock.reservation.domain.Reservation;
import dev.moksh.stocklock.reservation.dto.CreateReservationRequest;
import dev.moksh.stocklock.reservation.dto.ReservationResponse;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.reservation.service.ReservationService;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import javax.sql.DataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.web.server.ResponseStatusException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE)
@ActiveProfiles("postgres-test")
class PostgreSQLReservationConcurrencyTests {

	private static final Duration START_TIMEOUT = Duration.ofSeconds(20);
	private static final Duration RESULT_TIMEOUT = Duration.ofSeconds(60);

	@Container
	@ServiceConnection
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

	@Autowired
	private ReservationService reservationService;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Autowired
	private DataSource dataSource;

	@Autowired
	private Flyway flyway;

	@Test
	void usesPostgreSqlAndAppliesAllFlywayMigrations() throws SQLException {
		try (Connection connection = dataSource.getConnection()) {
			assertThat(connection.getMetaData().getDatabaseProductName()).isEqualTo("PostgreSQL");
		}

		assertThat(flyway.info().current()).isNotNull();
		assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("4");
	}

	@Test
	void concurrentReservationsCannotExceedAvailableQuantity() throws Exception {
		Resource resource = resource("over-reserve", 8);
		int workers = 24;
		CountDownLatch ready = new CountDownLatch(workers);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(workers);
		List<Future<Attempt>> futures = new ArrayList<>();

		for (int index = 0; index < workers; index++) {
			String idempotencyKey = "sl10-over-reserve-" + UUID.randomUUID();
			futures.add(executor.submit(reservationAttempt(ready, start, resource.getId(), 1, idempotencyKey)));
		}

		List<Attempt> attempts = collectConcurrentResults(ready, start, executor, futures);
		List<Attempt> successful = attempts.stream().filter(Attempt::succeeded).toList();
		List<Attempt> conflicts = attempts.stream()
				.filter(attempt -> Integer.valueOf(409).equals(attempt.statusCode())).toList();

		assertThat(successful).hasSize(8);
		assertThat(conflicts).hasSize(workers - 8);
		List<Reservation> persisted = reservationsFor(resource.getId());
		assertThat(persisted).hasSize(8);
		assertThat(persisted).allSatisfy(reservation -> assertThat(reservation.getQuantity()).isEqualTo(1));
		assertThat(persisted.stream().mapToInt(Reservation::getQuantity).sum()).isEqualTo(8);
		int remaining = resourceRepository.findById(resource.getId()).orElseThrow().getQuantity();
		assertThat(remaining).isZero();
		assertThat(remaining).isGreaterThanOrEqualTo(0);
	}

	@Test
	void concurrentEquivalentIdempotencyRequestsReturnTheSameReservation() throws Exception {
		Resource resource = resource("duplicate-idempotency", 20);
		String idempotencyKey = "sl10-duplicate-" + UUID.randomUUID();
		int workers = 10;
		CountDownLatch ready = new CountDownLatch(workers);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(workers);
		List<Future<Attempt>> futures = new ArrayList<>();

		for (int index = 0; index < workers; index++) {
			futures.add(executor.submit(reservationAttempt(ready, start, resource.getId(), 3, idempotencyKey)));
		}

		List<Attempt> attempts = collectConcurrentResults(ready, start, executor, futures);
		assertThat(attempts).allSatisfy(attempt -> assertThat(attempt.statusCode()).isNull());
		List<Long> reservationIds = attempts.stream().map(attempt -> attempt.response().id()).distinct().toList();
		assertThat(reservationIds).hasSize(1);
		assertThat(reservationRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
		List<Reservation> persisted = reservationsFor(resource.getId()).stream()
				.filter(reservation -> reservation.getIdempotencyKey().equals(idempotencyKey)).toList();
		assertThat(persisted).hasSize(1);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(17);
	}

	@Test
	void overlengthIdempotencyKeyInsertFailureRollsBackPostgreSqlQuantityUpdate() {
		Resource resource = resource("postgres-rollback", 5);
		String invalidKey = "x".repeat(256);

		assertThatThrownBy(() -> reservationService.create(
				new CreateReservationRequest(resource.getId(), 2), invalidKey))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(5);
		assertThat(reservationRepository.findByIdempotencyKey(invalidKey)).isEmpty();
		assertThat(reservationsFor(resource.getId())).isEmpty();
	}

	private Callable<Attempt> reservationAttempt(CountDownLatch ready, CountDownLatch start,
			Long resourceId, int quantity, String idempotencyKey) {
		return () -> {
			ready.countDown();
			if (!start.await(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS)) {
				throw new IllegalStateException("Timed out waiting for concurrent reservation start");
			}
			try {
				ReservationResponse response = reservationService.create(
						new CreateReservationRequest(resourceId, quantity), idempotencyKey);
				return new Attempt(response, null);
			} catch (ResponseStatusException exception) {
				return new Attempt(null, exception.getStatusCode().value());
			}
		};
	}

	private List<Attempt> collectConcurrentResults(CountDownLatch ready, CountDownLatch start,
			ExecutorService executor, List<Future<Attempt>> futures)
			throws InterruptedException, ExecutionException, TimeoutException {
		try {
			assertThat(ready.await(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
			start.countDown();
			List<Attempt> attempts = new ArrayList<>(futures.size());
			for (Future<Attempt> future : futures) {
				attempts.add(future.get(RESULT_TIMEOUT.toSeconds(), TimeUnit.SECONDS));
			}
			return attempts;
		} finally {
			start.countDown();
			executor.shutdownNow();
			assertThat(executor.awaitTermination(START_TIMEOUT.toSeconds(), TimeUnit.SECONDS)).isTrue();
		}
	}

	private Resource resource(String prefix, int quantity) {
		return resourceRepository.saveAndFlush(new Resource(prefix + "-" + UUID.randomUUID(), "SL-10 test", quantity));
	}

	private List<Reservation> reservationsFor(Long resourceId) {
		return reservationRepository.findAll().stream()
				.filter(reservation -> reservation.getResourceId().equals(resourceId))
				.toList();
	}

	private record Attempt(ReservationResponse response, Integer statusCode) {
		private boolean succeeded() {
			return response != null;
		}
	}
}
