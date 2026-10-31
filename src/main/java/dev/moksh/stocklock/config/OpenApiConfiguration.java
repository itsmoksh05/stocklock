package dev.moksh.stocklock.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.info.Info;

@OpenAPIDefinition(info = @Info(
		title = "StockLock API",
		description = "A quantity-based resource reservation engine with atomic availability updates and idempotent reservation creation.",
		version = "v1"))
public class OpenApiConfiguration {
}
