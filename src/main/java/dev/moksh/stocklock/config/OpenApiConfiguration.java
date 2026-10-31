package dev.moksh.stocklock.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Map;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@OpenAPIDefinition(info = @Info(
		title = "StockLock API",
		description = "A quantity-based resource reservation engine with atomic availability updates and idempotent reservation creation.",
		version = "v1"))
public class OpenApiConfiguration {

	@Bean
	OpenApiCustomizer nullableEnvelopeFields() {
		return openApi -> {
			markNullable(openApi, "ApiResponse", "data");
			openApi.getComponents().getSchemas().get("ApiErrorResponse").setNullable(true);
		};
	}

	@SuppressWarnings({"rawtypes"})
	private void markNullable(OpenAPI openApi, String schemaName, String propertyName) {
		Map<String, Schema> schemas = openApi.getComponents().getSchemas();
		Schema parentSchema = schemas.get(schemaName);
		Schema propertySchema = (Schema) parentSchema.getProperties().get(propertyName);
		propertySchema.setNullable(true);
	}
}
