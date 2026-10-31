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
import dev.moksh.stocklock.resource.persistence.ResourceRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ReservationLifecycleIntegrationTests {

	@Autowired
	private MockMvc mockMvc;

	@Autowired
	private ResourceRepository resourceRepository;

	@Autowired
	private ReservationRepository reservationRepository;

	@Test
	void completesResourceReservationIdempotencyAndCancellationLifecycle() throws Exception {
		String resourceName = "Lifecycle Test Resource " + UUID.randomUUID();
		String idempotencyKey = "sl12-lifecycle-" + UUID.randomUUID();

		MvcResult resourceCreated = mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"name":"%s","description":"End-to-end integration test","quantity":10}
							""".formatted(resourceName)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.name", is(resourceName)))
				.andExpect(jsonPath("$.data.quantity", is(10)))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn();
		long resourceId = idFrom(resourceCreated, "$.data.id");

		assertResourceDetails(resourceId, resourceName, 10);
		assertAvailability(resourceId, 10);

		String reservationRequest = """
				{"resourceId":%d,"quantity":3}
				""".formatted(resourceId);
		MvcResult reservationCreated = createReservation(reservationRequest, idempotencyKey)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.resourceId", is((int) resourceId)))
				.andExpect(jsonPath("$.data.quantity", is(3)))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn();
		long reservationId = idFrom(reservationCreated, "$.data.id");
		assertReservationStatus(reservationId, "ACTIVE");

		MvcResult replay = createReservation(reservationRequest, idempotencyKey)
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.id", is((int) reservationId)))
				.andExpect(jsonPath("$.error").doesNotExist())
				.andReturn();
		assertThat(idFrom(replay, "$.data.id")).isEqualTo(reservationId);
		assertThat(reservationRepository.findByIdempotencyKey(idempotencyKey)).isPresent();
		assertThat(reservationRepository.findByIdempotencyKey(idempotencyKey).orElseThrow().getId())
				.isEqualTo(reservationId);
		assertResourceDetails(resourceId, resourceName, 7);
		assertAvailability(resourceId, 7);

		cancelReservation(reservationId);
		assertReservationStatus(reservationId, "CANCELLED");
		assertAvailability(resourceId, 10);

		cancelReservation(reservationId);
		assertReservationStatus(reservationId, "CANCELLED");
		assertAvailability(resourceId, 10);
		assertThat(reservationRepository.findById(reservationId)).isPresent();
	}

	private void assertResourceDetails(long resourceId, String name, int quantity) throws Exception {
		mockMvc.perform(get("/api/v1/resources/{id}", resourceId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.id", is((int) resourceId)))
				.andExpect(jsonPath("$.data.name", is(name)))
				.andExpect(jsonPath("$.data.quantity", is(quantity)))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	private void assertAvailability(long resourceId, int quantity) throws Exception {
		mockMvc.perform(get("/api/v1/resources/{id}/availability", resourceId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.resourceId", is((int) resourceId)))
				.andExpect(jsonPath("$.data.availableQuantity", is(quantity)))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	private void assertReservationStatus(long reservationId, String expectedStatus) throws Exception {
		mockMvc.perform(get("/api/v1/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.id", is((int) reservationId)))
				.andExpect(jsonPath("$.data.status", is(expectedStatus)))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	private void cancelReservation(long reservationId) throws Exception {
		mockMvc.perform(delete("/api/v1/reservations/{id}", reservationId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.success", is(true)))
				.andExpect(jsonPath("$.data.id", is((int) reservationId)))
				.andExpect(jsonPath("$.data.status", is("CANCELLED")))
				.andExpect(jsonPath("$.error").doesNotExist());
	}

	private org.springframework.test.web.servlet.ResultActions createReservation(
			String request, String idempotencyKey) throws Exception {
		return mockMvc.perform(post("/api/v1/reservations")
				.header("Idempotency-Key", idempotencyKey)
				.contentType(MediaType.APPLICATION_JSON)
				.content(request));
	}

	private long idFrom(MvcResult result, String jsonPath) throws Exception {
		return ((Number) JsonPath.read(result.getResponse().getContentAsString(), jsonPath)).longValue();
	}
}
