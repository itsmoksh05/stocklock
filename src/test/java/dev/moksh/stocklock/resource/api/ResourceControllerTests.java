package dev.moksh.stocklock.resource.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Conference room","description":"Floor 2","quantity":3}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.name", is("Conference room")))
                .andExpect(jsonPath("$.description", is("Floor 2")))
                .andExpect(jsonPath("$.quantity", is(3)));
    }

    @Test
    void rejectsMissingNameAndQuantity() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void rejectsNegativeQuantity() throws Exception {
        mockMvc.perform(post("/api/resources")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"Desk","quantity":-1}
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void retrievesResourceDetailsAndAvailableQuantity() throws Exception {
        Resource saved = resourceRepository.saveAndFlush(
                new Resource("Conference Room", "Meeting room on floor 2", 10));

        mockMvc.perform(get("/api/resources/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.name", is("Conference Room")))
                .andExpect(jsonPath("$.description", is("Meeting room on floor 2")))
                .andExpect(jsonPath("$.quantity", is(10)));
    }

    @Test
    void retrievesCurrentAvailableQuantity() throws Exception {
        Resource saved = resourceRepository.saveAndFlush(
                new Resource("Conference Room", "Meeting room on floor 2", 10));

        mockMvc.perform(get("/api/resources/{id}/availability", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resourceId", is(saved.getId().intValue())))
                .andExpect(jsonPath("$.availableQuantity", is(10)));
    }

    @Test
    void returnsNotFoundForUnknownResourceId() throws Exception {
        mockMvc.perform(get("/api/resources/{id}", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    void returnsNotFoundForUnknownAvailabilityId() throws Exception {
        mockMvc.perform(get("/api/resources/{id}/availability", Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }
}
