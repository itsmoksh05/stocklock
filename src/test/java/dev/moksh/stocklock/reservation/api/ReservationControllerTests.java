package dev.moksh.stocklock.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.jayway.jsonpath.JsonPath;
import dev.moksh.stocklock.reservation.persistence.ReservationRepository;
import dev.moksh.stocklock.resource.domain.Resource;
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;

import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationControllerTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	void createsReservationAndDecrementsAvailableQuantity() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", "Meeting room", 10));
		long reservationCountBefore = reservationRepository.count();

		createReservation(resource.getId(), 3, "success-key")
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andExpect(jsonPath("$.data.id").isNumber())
				.andExpect(jsonPath("$.data.resourceId", is(resource.getId().intValue())))
				.andExpect(jsonPath("$.data.quantity", is(3)));

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
	}

	@Test
	void returnsConflictAndDoesNotCreateReservationWhenQuantityIsInsufficient() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Desk", null, 2));
		long reservationCountBefore = reservationRepository.count();

		createReservation(resource.getId(), 3, "insufficient-key")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.status", is(409)))
				.andExpect(jsonPath("$.error.message", is("Insufficient available quantity")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(2);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore);
	}

	@Test
	void returnsNotFoundForUnknownResource() throws Exception {
		mockMvc.perform(post("/api/reservations")
					.header("Idempotency-Key", "unknown-resource-key")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"resourceId":9223372036854775807,"quantity":1}
							"""))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.status", is(404)))
				.andExpect(jsonPath("$.error.message", is("Resource not found")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
	}

	@Test
	void rejectsZeroOrNegativeQuantities() throws Exception {
		for (int quantity : new int[] {0, -1}) {
			mockMvc.perform(post("/api/reservations")
						.header("Idempotency-Key", "invalid-quantity-" + quantity)
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"resourceId":1,"quantity":%d}
								""".formatted(quantity)))
					.andExpect(status().isBadRequest());
		}
		mockMvc.perform(post("/api/reservations")
					.header("Idempotency-Key", "missing-quantity-key")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"resourceId\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.quantity").exists());
	}

	@Test
	void rejectsMissingOrNonpositiveResourceId() throws Exception {
		for (String body : new String[] {
				"{\"quantity\":1}", "{\"resourceId\":0,\"quantity\":1}", "{\"resourceId\":-1,\"quantity\":1}"}) {
			mockMvc.perform(post("/api/reservations")
						.header("Idempotency-Key", "invalid-resource-" + body.hashCode())
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
		}
	}

	@Test
	void replaysOriginalReservationWithoutRepeatingTheDecrement() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		long reservationCountBefore = reservationRepository.count();

		MvcResult first = createReservation(resource.getId(), 3, "replay-key")
				.andExpect(status().isCreated()).andReturn();
		MvcResult replay = createReservation(resource.getId(), 3, "replay-key")
				.andExpect(status().isCreated()).andReturn();

		assertThat(JsonPath.<Integer>read(replay.getResponse().getContentAsString(), "$.data.id"))
				.isEqualTo(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.data.id"));
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
	}

	@Test
	void rejectsSameKeyWithDifferentQuantity() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		createReservation(resource.getId(), 3, "quantity-mismatch-key").andExpect(status().isCreated());
		long reservationCountAfterCreate = reservationRepository.count();

		createReservation(resource.getId(), 4, "quantity-mismatch-key")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.message", is("Idempotency key was already used with a different request")));

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountAfterCreate);
	}

	@Test
	void rejectsSameKeyWithDifferentResource() throws Exception {
		Resource firstResource = resourceRepository.saveAndFlush(new Resource("Room A", null, 10));
		Resource secondResource = resourceRepository.saveAndFlush(new Resource("Room B", null, 10));
		createReservation(firstResource.getId(), 3, "resource-mismatch-key").andExpect(status().isCreated());
		long reservationCountAfterCreate = reservationRepository.count();

		createReservation(secondResource.getId(), 3, "resource-mismatch-key")
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.error.message", is("Idempotency key was already used with a different request")));

		assertThat(resourceRepository.findById(firstResource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(resourceRepository.findById(secondResource.getId()).orElseThrow().getQuantity()).isEqualTo(10);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountAfterCreate);
	}

	@Test
	void differentKeysCreateSeparateReservationsForEquivalentRequests() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		long reservationCountBefore = reservationRepository.count();

		MvcResult first = createReservation(resource.getId(), 2, "separate-key-one")
				.andExpect(status().isCreated()).andReturn();
		MvcResult second = createReservation(resource.getId(), 2, "separate-key-two")
				.andExpect(status().isCreated()).andReturn();

		assertThat(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.data.id"))
				.isNotEqualTo(JsonPath.<Integer>read(second.getResponse().getContentAsString(), "$.data.id"));
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(6);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 2);
	}

	@Test
	void rejectsMissingBlankAndOversizedIdempotencyKeys() throws Exception {
		String body = "{\"resourceId\":1,\"quantity\":1}";
		mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Required request header is missing")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
		mockMvc.perform(post("/api/reservations").header("Idempotency-Key", "  ")
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Request validation failed")))
				.andExpect(jsonPath("$.error.fieldErrors.idempotencyKey").exists());
		mockMvc.perform(post("/api/reservations").header("Idempotency-Key", "k".repeat(256))
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Request validation failed")))
				.andExpect(jsonPath("$.error.fieldErrors.idempotencyKey").exists());
	}

	@Test
	void concurrentDuplicateRequestsReturnOneReservationAndDecrementOnce() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 20));
		long reservationCountBefore = reservationRepository.count();
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);

		Callable<MvcResult> request = () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent request start timed out");
			}
			return createReservation(resource.getId(), 3, "concurrent-duplicate-key").andReturn();
		};

		try {
			Future<MvcResult> firstFuture = executor.submit(request);
			Future<MvcResult> secondFuture = executor.submit(request);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			MvcResult first = firstFuture.get(20, TimeUnit.SECONDS);
			MvcResult second = secondFuture.get(20, TimeUnit.SECONDS);

			assertThat(first.getResponse().getStatus()).isEqualTo(201);
			assertThat(second.getResponse().getStatus()).isEqualTo(201);
			assertThat(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.data.id"))
					.isEqualTo(JsonPath.<Integer>read(second.getResponse().getContentAsString(), "$.data.id"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(17);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
	}

	@Test
	void retrievesExistingReservationDetails() throws Exception {
		MvcResult created = createReservation(
				resourceRepository.saveAndFlush(new Resource("Room", null, 10)).getId(), 2, "details-key")
				.andExpect(status().isCreated()).andReturn();
		int reservationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(get("/api/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id", is(reservationId)))
				.andExpect(jsonPath("$.data.resourceId").isNumber())
				.andExpect(jsonPath("$.data.quantity", is(2)))
				.andExpect(jsonPath("$.data.status", is("ACTIVE")));
	}

	@Test
	void returnsNotFoundForUnknownReservationDetailsAndCancellation() throws Exception {
		mockMvc.perform(get("/api/reservations/{id}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.status", is(404)))
				.andExpect(jsonPath("$.error.message", is("Reservation not found")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
		mockMvc.perform(delete("/api/reservations/{id}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.status", is(404)))
				.andExpect(jsonPath("$.error.message", is("Reservation not found")));
	}

	@Test
	void cancellationRestoresQuantityAndKeepsReservationWithCancelledStatus() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		MvcResult created = createReservation(resource.getId(), 3, "cancel-key")
				.andExpect(status().isCreated()).andReturn();
		int reservationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
		long reservationCount = reservationRepository.count();
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);

		mockMvc.perform(delete("/api/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.id", is(reservationId)))
				.andExpect(jsonPath("$.data.resourceId", is(resource.getId().intValue())))
				.andExpect(jsonPath("$.data.quantity", is(3)))
				.andExpect(jsonPath("$.data.status", is("CANCELLED")));

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(10);
		assertThat(reservationRepository.count()).isEqualTo(reservationCount);
		mockMvc.perform(get("/api/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status", is("CANCELLED")));
	}

	@Test
	void repeatedCancellationDoesNotRestoreQuantityAgain() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		int reservationId = JsonPath.read(createReservation(resource.getId(), 4, "repeat-cancel-key")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");

		mockMvc.perform(delete("/api/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status", is("CANCELLED")));
		mockMvc.perform(delete("/api/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status", is("CANCELLED")));

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(10);
	}

	@Test
	void replayAfterCancellationReturnsOriginalReservationWithoutChangingAvailability() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		MvcResult created = createReservation(resource.getId(), 3, "cancelled-replay-key")
				.andExpect(status().isCreated()).andReturn();
		int reservationId = JsonPath.read(created.getResponse().getContentAsString(), "$.data.id");
		mockMvc.perform(delete("/api/reservations/{id}", reservationId)).andExpect(status().isOk());
		long reservationCount = reservationRepository.count();

		MvcResult replay = createReservation(resource.getId(), 3, "cancelled-replay-key")
				.andExpect(status().isCreated()).andReturn();

		assertThat(JsonPath.<Integer>read(replay.getResponse().getContentAsString(), "$.data.id")).isEqualTo(reservationId);
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(10);
		assertThat(reservationRepository.count()).isEqualTo(reservationCount);
	}

	@Test
	void concurrentCancellationRestoresQuantityOnlyOnce() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		int reservationId = JsonPath.read(createReservation(resource.getId(), 4, "concurrent-cancel-key")
				.andExpect(status().isCreated()).andReturn().getResponse().getContentAsString(), "$.data.id");
		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService executor = Executors.newFixedThreadPool(2);
		Callable<MvcResult> request = () -> {
			ready.countDown();
			if (!start.await(5, TimeUnit.SECONDS)) {
				throw new IllegalStateException("Concurrent cancellation start timed out");
			}
			return mockMvc.perform(delete("/api/reservations/{id}", reservationId)).andReturn();
		};

		try {
			Future<MvcResult> firstFuture = executor.submit(request);
			Future<MvcResult> secondFuture = executor.submit(request);
			assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
			start.countDown();
			MvcResult first = firstFuture.get(20, TimeUnit.SECONDS);
			MvcResult second = secondFuture.get(20, TimeUnit.SECONDS);
			assertThat(first.getResponse().getStatus()).isEqualTo(200);
			assertThat(second.getResponse().getStatus()).isEqualTo(200);
			assertThat(JsonPath.<String>read(first.getResponse().getContentAsString(), "$.data.status")).isEqualTo("CANCELLED");
			assertThat(JsonPath.<String>read(second.getResponse().getContentAsString(), "$.data.status")).isEqualTo("CANCELLED");
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(10);
	}

	private ResultActions createReservation(Long resourceId, int quantity,
			String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/reservations")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"resourceId":%d,"quantity":%d}
						""".formatted(resourceId, quantity)));
	}
}
