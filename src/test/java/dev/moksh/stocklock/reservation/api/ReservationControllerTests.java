package dev.moksh.stocklock.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
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
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.resourceId", is(resource.getId().intValue())))
				.andExpect(jsonPath("$.quantity", is(3)));

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
	}

	@Test
	void returnsConflictAndDoesNotCreateReservationWhenQuantityIsInsufficient() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Desk", null, 2));
		long reservationCountBefore = reservationRepository.count();

		createReservation(resource.getId(), 3, "insufficient-key")
				.andExpect(status().isConflict());

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
				.andExpect(status().isNotFound());
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
	}

	@Test
	void rejectsMissingOrNonpositiveResourceId() throws Exception {
		for (String body : new String[] {"{\"quantity\":1}", "{\"resourceId\":0,\"quantity\":1}"}) {
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

		assertThat(JsonPath.<Integer>read(replay.getResponse().getContentAsString(), "$.id"))
				.isEqualTo(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.id"));
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
	}

	@Test
	void rejectsSameKeyWithDifferentQuantity() throws Exception {
		Resource resource = resourceRepository.saveAndFlush(new Resource("Room", null, 10));
		createReservation(resource.getId(), 3, "quantity-mismatch-key").andExpect(status().isCreated());
		long reservationCountAfterCreate = reservationRepository.count();

		createReservation(resource.getId(), 4, "quantity-mismatch-key").andExpect(status().isConflict());

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(7);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountAfterCreate);
	}

	@Test
	void rejectsSameKeyWithDifferentResource() throws Exception {
		Resource firstResource = resourceRepository.saveAndFlush(new Resource("Room A", null, 10));
		Resource secondResource = resourceRepository.saveAndFlush(new Resource("Room B", null, 10));
		createReservation(firstResource.getId(), 3, "resource-mismatch-key").andExpect(status().isCreated());
		long reservationCountAfterCreate = reservationRepository.count();

		createReservation(secondResource.getId(), 3, "resource-mismatch-key").andExpect(status().isConflict());

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

		assertThat(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.id"))
				.isNotEqualTo(JsonPath.<Integer>read(second.getResponse().getContentAsString(), "$.id"));
		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(6);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 2);
	}

	@Test
	void rejectsMissingBlankAndOversizedIdempotencyKeys() throws Exception {
		String body = "{\"resourceId\":1,\"quantity\":1}";
		mockMvc.perform(post("/api/reservations").contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/reservations").header("Idempotency-Key", "  ")
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
		mockMvc.perform(post("/api/reservations").header("Idempotency-Key", "k".repeat(256))
					.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isBadRequest());
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
			assertThat(JsonPath.<Integer>read(first.getResponse().getContentAsString(), "$.id"))
					.isEqualTo(JsonPath.<Integer>read(second.getResponse().getContentAsString(), "$.id"));
		} finally {
			start.countDown();
			executor.shutdownNow();
		}

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(17);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore + 1);
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
