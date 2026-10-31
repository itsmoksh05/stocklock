package dev.moksh.stocklock.openapi;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class OpenApiDocumentationTests {

	@Autowired
	private MockMvc mockMvc;

	@Test
	void publishesVersionedApiPathsAndConcreteRequestSchemas() throws Exception {
		mockMvc.perform(get("/v3/api-docs"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.openapi", is("3.0.1")))
				.andExpect(jsonPath("$.info.title", is("StockLock API")))
				.andExpect(jsonPath("$.info.version", is("v1")))
				.andExpect(jsonPath("$.paths['/api/v1/resources'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/resources/{id}'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/resources/{id}/availability'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/reservations'].post").exists())
				.andExpect(jsonPath("$.paths['/api/v1/reservations/{id}'].get").exists())
				.andExpect(jsonPath("$.paths['/api/v1/reservations/{id}'].delete").exists())
				.andExpect(jsonPath("$.paths['/api/v1/resources'].post.responses['201'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseResourceResponse")))
				.andExpect(jsonPath("$.paths['/api/v1/resources/{id}'].get.responses['200'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseResourceResponse")))
				.andExpect(jsonPath("$.paths['/api/v1/resources/{id}/availability'].get.responses['200'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseResourceAvailabilityResponse")))
				.andExpect(jsonPath("$.paths['/api/v1/reservations'].post.responses['201'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseReservationResponse")))
				.andExpect(jsonPath("$.paths['/api/v1/reservations'].post.parameters[0].name", is("Idempotency-Key")))
				.andExpect(jsonPath("$.paths['/api/v1/reservations'].post.parameters[0].required", is(true)))
				.andExpect(jsonPath("$.paths['/api/v1/reservations'].post.parameters[0].schema.maxLength", is(255)))
				.andExpect(jsonPath("$.paths['/api/v1/reservations/{id}'].get.responses['200'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseReservationDetailsResponse")))
				.andExpect(jsonPath("$.paths['/api/v1/reservations/{id}'].delete.responses['200'].content['*/*'].schema.$ref",
						is("#/components/schemas/ApiResponseReservationDetailsResponse")))
				.andExpect(content().string(containsString("Idempotency-Key")))
				.andExpect(content().string(containsString("CreateReservationRequest")))
				.andExpect(content().string(containsString("ReservationDetailsResponse")))
				.andExpect(content().string(containsString("CANCELLED")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseResourceResponse.properties.data.$ref",
						is("#/components/schemas/ResourceResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseResourceResponse.properties.error.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.ApiResponseResourceResponse.properties.error.allOf[0].$ref",
						is("#/components/schemas/ApiErrorResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationResponse.properties.data.$ref",
						is("#/components/schemas/ReservationResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationResponse.properties.error.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationResponse.properties.error.allOf[0].$ref",
						is("#/components/schemas/ApiErrorResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationDetailsResponse.properties.data.$ref",
						is("#/components/schemas/ReservationDetailsResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationDetailsResponse.properties.error.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.ApiResponseReservationDetailsResponse.properties.error.allOf[0].$ref",
						is("#/components/schemas/ApiErrorResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponseResourceAvailabilityResponse.properties.error.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.CreateResourceRequest.properties.name.maxLength", is(120)))
				.andExpect(jsonPath("$.components.schemas.CreateResourceRequest.properties.quantity.minimum", is(0)))
				.andExpect(jsonPath("$.components.schemas.CreateReservationRequest.properties.quantity.minimum", is(1)))
				.andExpect(jsonPath("$.components.schemas.ApiResponse.properties.data.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.ApiResponse.properties.error.allOf[0].$ref", is("#/components/schemas/ApiErrorResponse")))
				.andExpect(jsonPath("$.components.schemas.ApiResponse.properties.error.nullable", is(true)))
				.andExpect(jsonPath("$.components.schemas.ReservationDetailsResponse.properties.status.enum[0]", is("ACTIVE")))
				.andExpect(jsonPath("$.components.schemas.ReservationDetailsResponse.properties.status.enum[1]", is("CANCELLED")));
	}

	@Test
	void servesSwaggerUiResources() throws Exception {
		mockMvc.perform(get("/swagger-ui.html"))
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string("Location", containsString("/swagger-ui/index.html")));

		mockMvc.perform(get("/swagger-ui/index.html"))
				.andExpect(status().isOk())
				.andExpect(content().contentTypeCompatibleWith("text/html"));
	}
}
