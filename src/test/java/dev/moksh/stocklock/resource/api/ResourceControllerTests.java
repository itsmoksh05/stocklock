package dev.moksh.stocklock.resource.api;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
}
