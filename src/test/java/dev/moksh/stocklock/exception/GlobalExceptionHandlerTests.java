package dev.moksh.stocklock.exception;

import static org.hamcrest.Matchers.is;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

class GlobalExceptionHandlerTests {

	@Test
	void unexpectedExceptionsReturnSanitizedStandardResponse() throws Exception {
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new UnexpectedExceptionController())
				.setControllerAdvice(new GlobalExceptionHandler())
				.build();

		mockMvc.perform(get("/test/unexpected"))
				.andExpect(status().isInternalServerError())
				.andExpect(content().contentTypeCompatibleWith("application/json"))
				.andExpect(jsonPath("$.success", is(false)))
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.timestamp").exists())
				.andExpect(jsonPath("$.error.status", is(500)))
				.andExpect(jsonPath("$.error.error", is("Internal Server Error")))
				.andExpect(jsonPath("$.error.message", is("An unexpected error occurred.")))
				.andExpect(jsonPath("$.error.path", is("/test/unexpected")))
				.andExpect(jsonPath("$.error.fieldErrors").isMap())
				.andExpect(jsonPath("$.error.trace").doesNotExist())
				.andExpect(jsonPath("$.error.exception").doesNotExist())
				.andExpect(content().string(org.hamcrest.Matchers.not(
						org.hamcrest.Matchers.containsString("sensitive database detail"))));
	}

	@RestController
	static class UnexpectedExceptionController {
		@GetMapping("/test/unexpected")
		String fail() {
			throw new IllegalStateException("sensitive database detail");
		}
	}
}
