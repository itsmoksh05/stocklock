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
				.andExpect(jsonPath("$.timestamp").exists())
				.andExpect(jsonPath("$.status", is(500)))
				.andExpect(jsonPath("$.error", is("Internal Server Error")))
				.andExpect(jsonPath("$.message", is("An unexpected error occurred.")))
				.andExpect(jsonPath("$.path", is("/test/unexpected")))
				.andExpect(jsonPath("$.fieldErrors").isMap())
				.andExpect(jsonPath("$.trace").doesNotExist())
				.andExpect(jsonPath("$.exception").doesNotExist())
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
