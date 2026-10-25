package com.nexaops.backend.integration;

public record AiIntegrationHealthResponse(
        ComponentHealth javaBackend,
        ComponentHealth pythonAiService,
        ComponentHealth ollama,
        ModelHealth generationModel,
        ModelHealth embeddingModel
) {
    public record ComponentHealth(HealthState status, String message) {
    }

    public record ModelHealth(
            String model,
            HealthState status,
            Boolean available,
            String message
    ) {
    }

    public enum HealthState {
        UP,
        DOWN,
        UNKNOWN
    }
}
