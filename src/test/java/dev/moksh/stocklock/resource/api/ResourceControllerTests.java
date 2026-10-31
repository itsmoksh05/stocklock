package dev.moksh.stocklock.resource.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
class ResourceControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ResourceRepository resourceRepository;

    @Test
    void createsResourceAndReturnsCreatedResource() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Conference room","description":"Floor 2","quantity":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name", is("Conference room")))
                .andExpect(jsonPath("$.data.description", is("Floor 2")))
                .andExpect(jsonPath("$.data.quantity", is(3)));
    }

    @Test
	void rejectsMissingResourceName() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .contentType(MediaType.APPLICATION_JSON)
					.content("{\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Request validation failed")))
				.andExpect(jsonPath("$.error.fieldErrors.name").exists());
	}

	@Test
	void rejectsMissingResourceQuantity() throws Exception {
		mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Desk\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.quantity").exists());
	}

	@Test
	void rejectsBlankOrOversizedResourceNameAndOversizedDescription() throws Exception {
		mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"  \",\"quantity\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.name").exists());
		mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"%s\",\"quantity\":1}".formatted("n".repeat(121))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.name").exists());
		mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Desk\",\"description\":\"%s\",\"quantity\":1}"
							.formatted("d".repeat(501))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.description").exists());
    }

    @Test
    void rejectsNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/v1/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Desk","quantity":-1}
                                """))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fieldErrors.quantity").exists());
	}

	@Test
	void acceptsZeroInitialAvailability() throws Exception {
		mockMvc.perform(post("/api/v1/resources")
					.contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Empty shelf\",\"quantity\":0}"))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.quantity", is(0)));
    }

    @Test
    void retrievesResourceDetailsAndAvailableQuantity() throws Exception {
        Resource saved = resourceRepository.saveAndFlush(
                new Resource("Conference Room", "Meeting room on floor 2", 10));

        mockMvc.perform(get("/api/v1/resources/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.data.name", is("Conference Room")))
                .andExpect(jsonPath("$.data.description", is("Meeting room on floor 2")))
                .andExpect(jsonPath("$.data.quantity", is(10)));
    }

    @Test
    void retrievesCurrentAvailableQuantity() throws Exception {
        Resource saved = resourceRepository.saveAndFlush(
                new Resource("Conference Room", "Meeting room on floor 2", 10));

        mockMvc.perform(get("/api/v1/resources/{id}/availability", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success", is(true)))
                .andExpect(jsonPath("$.error").doesNotExist())
                .andExpect(jsonPath("$.data.resourceId", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.data.availableQuantity", is(10)));
    }

    @Test
    void returnsNotFoundForUnknownResourceId() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{id}", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.status", is(404)))
				.andExpect(jsonPath("$.error.error", is("Not Found")))
				.andExpect(jsonPath("$.error.message", is("Resource not found")))
				.andExpect(jsonPath("$.error.path", is("/api/v1/resources/9223372036854775807")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
    }

    @Test
    void returnsNotFoundForUnknownAvailabilityId() throws Exception {
        mockMvc.perform(get("/api/v1/resources/{id}/availability", Long.MAX_VALUE))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.status", is(404)))
				.andExpect(jsonPath("$.error.error", is("Not Found")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
	}

	@Test
	void returnsStandardErrorsForMalformedBodiesAndInvalidPathVariables() throws Exception {
		mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON).content("{\"name\":"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.timestamp").isString())
				.andExpect(jsonPath("$.error.status", is(400)))
				.andExpect(jsonPath("$.error.error", is("Bad Request")))
				.andExpect(jsonPath("$.error.message", is("Invalid request body")))
				.andExpect(jsonPath("$.error.path", is("/api/v1/resources")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap());
		mockMvc.perform(post("/api/v1/resources").contentType(MediaType.APPLICATION_JSON)
					.content("{\"name\":\"Desk\",\"quantity\":\"invalid\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Invalid request body")));
		mockMvc.perform(get("/api/v1/resources/not-a-number"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.message", is("Invalid request parameter")))
				.andExpect(jsonPath("$.error.path", is("/api/v1/resources/not-a-number")));
	}

	@Test
	void returnsStandardErrorsForUnsupportedMethodAndMediaType() throws Exception {
		mockMvc.perform(put("/api/v1/resources"))
				.andExpect(status().isMethodNotAllowed())
				.andExpect(jsonPath("$.error.status", is(405)))
				.andExpect(jsonPath("$.error.message", is("HTTP method not allowed")));
		mockMvc.perform(post("/api/v1/resources").contentType(MediaType.TEXT_PLAIN).content("resource"))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.error.status", is(415)))
				.andExpect(jsonPath("$.error.message", is("Unsupported media type")));
    }
}
