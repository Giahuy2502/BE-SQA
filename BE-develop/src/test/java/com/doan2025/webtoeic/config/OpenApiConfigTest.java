package com.doan2025.webtoeic.config;

import io.swagger.v3.oas.models.OpenAPI;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenApiConfigTest {

    // UTC-OA-001: Smoke test OpenAPI được tạo đúng info/server và có bearerAuth security scheme
    @Test
    void openAPI_shouldContainInfoServerAndBearerScheme() {
        // Given
        OpenApiConfig config = new OpenApiConfig();
        String title = "API";
        String version = "1.0";
        String description = "desc";
        String server = "http://localhost:8888";

        // When
        OpenAPI api = config.openAPI(title, version, description, server);

        // Then
        assertNotNull(api);
        assertNotNull(api.getInfo());
        assertEquals(title, api.getInfo().getTitle());
        assertEquals(version, api.getInfo().getVersion());
        assertEquals(description, api.getInfo().getDescription());

        assertNotNull(api.getServers());
        assertEquals(1, api.getServers().size());
        assertEquals(server, api.getServers().get(0).getUrl());

        assertNotNull(api.getComponents());
        assertNotNull(api.getComponents().getSecuritySchemes());
        assertTrue(api.getComponents().getSecuritySchemes().containsKey("bearerAuth"));
    }
}

