package dev.moksh.stocklock.reservation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

		mockMvc.perform(post("/api/reservations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"resourceId":%d,"quantity":3}
							""".formatted(resource.getId())))
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

		mockMvc.perform(post("/api/reservations")
					.contentType(MediaType.APPLICATION_JSON)
					.content("""
							{"resourceId":%d,"quantity":3}
							""".formatted(resource.getId())))
				.andExpect(status().isConflict());

		assertThat(resourceRepository.findById(resource.getId()).orElseThrow().getQuantity()).isEqualTo(2);
		assertThat(reservationRepository.count()).isEqualTo(reservationCountBefore);
	}

	@Test
	void returnsNotFoundForUnknownResource() throws Exception {
		mockMvc.perform(post("/api/reservations")
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
						.contentType(MediaType.APPLICATION_JSON)
						.content(body))
				.andExpect(status().isBadRequest());
		}
	}
}
