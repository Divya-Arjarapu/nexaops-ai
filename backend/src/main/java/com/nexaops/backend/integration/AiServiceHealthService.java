package com.nexaops.backend.integration;

import java.util.Map;

import com.nexaops.backend.integration.AiIntegrationHealthResponse.ComponentHealth;
import com.nexaops.backend.integration.AiIntegrationHealthResponse.HealthState;
import com.nexaops.backend.integration.AiIntegrationHealthResponse.ModelHealth;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
public class AiServiceHealthService {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    private final RestClient aiServiceRestClient;
    private final String generationModel;
    private final String embeddingModel;

    public AiServiceHealthService(
            RestClient aiServiceRestClient,
            @Value("${nexaops.ai-service.generation-model}") String generationModel,
            @Value("${nexaops.ai-service.embedding-model}") String embeddingModel
    ) {
        this.aiServiceRestClient = aiServiceRestClient;
        this.generationModel = generationModel;
        this.embeddingModel = embeddingModel;
    }

    public AiIntegrationHealthResponse checkHealth() {
        ResponseEntity<Map<String, Object>> pythonResponse;
        try {
            pythonResponse = get("/health");
        } catch (RestClientException exception) {
            return pythonUnavailable("Could not reach the Python AI service: " + messageOf(exception));
        }

        Map<String, Object> pythonBody = bodyOf(pythonResponse);
        if (!pythonResponse.getStatusCode().is2xxSuccessful()
                || !"ok".equals(stringValue(pythonBody.get("status")))) {
            return pythonUnavailable(pythonFailureMessage(pythonResponse, pythonBody));
        }

        ComponentHealth pythonHealth = new ComponentHealth(
                HealthState.UP,
                "Python AI service is reachable."
        );

        ResponseEntity<Map<String, Object>> diagnosticsResponse;
        try {
            diagnosticsResponse = get("/health/ollama");
        } catch (RestClientException exception) {
            return diagnosticsUnavailable(
                    pythonHealth,
                    "Could not retrieve Ollama diagnostics from the Python AI service: "
                            + messageOf(exception)
            );
        }

        if ((!diagnosticsResponse.getStatusCode().is2xxSuccessful()
                && diagnosticsResponse.getStatusCode().value() != 503)
                || diagnosticsResponse.getBody() == null) {
            return diagnosticsUnavailable(
                    pythonHealth,
                    "Python AI service returned HTTP "
                            + diagnosticsResponse.getStatusCode().value()
                            + " without usable Ollama diagnostics."
            );
        }

        Map<String, Object> diagnostics = bodyOf(diagnosticsResponse);
        Map<?, ?> ollamaPayload = mapValue(diagnostics.get("ollama"));
        ComponentHealth ollamaHealth = componentHealth(
                stringValue(ollamaPayload.get("status")),
                stringValue(ollamaPayload.get("message")),
                "Python AI service did not report Ollama status."
        );
        ModelHealth generationHealth = modelHealth(
                diagnostics.get("generation_model"), generationModel
        );
        ModelHealth embeddingHealth = modelHealth(
                diagnostics.get("embedding_model"), embeddingModel
        );

        return new AiIntegrationHealthResponse(
                backendHealth(),
                pythonHealth,
                ollamaHealth,
                generationHealth,
                embeddingHealth
        );
    }

    private ResponseEntity<Map<String, Object>> get(String path) {
        return aiServiceRestClient.get()
                .uri(path)
                .accept(MediaType.APPLICATION_JSON)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> { })
                .toEntity(JSON_MAP);
    }

    private AiIntegrationHealthResponse pythonUnavailable(String message) {
        return new AiIntegrationHealthResponse(
                backendHealth(),
                new ComponentHealth(HealthState.DOWN, message),
                new ComponentHealth(
                        HealthState.UNKNOWN,
                        "Not checked because the Python AI service is unavailable."
                ),
                unknownModel(generationModel, "Not checked because the Python AI service is unavailable."),
                unknownModel(embeddingModel, "Not checked because the Python AI service is unavailable.")
        );
    }

    private AiIntegrationHealthResponse diagnosticsUnavailable(
            ComponentHealth pythonHealth,
            String message
    ) {
        return new AiIntegrationHealthResponse(
                backendHealth(),
                pythonHealth,
                new ComponentHealth(HealthState.UNKNOWN, message),
                unknownModel(generationModel, message),
                unknownModel(embeddingModel, message)
        );
    }

    private static ComponentHealth backendHealth() {
        return new ComponentHealth(HealthState.UP, "Java backend is responding.");
    }

    private static ComponentHealth componentHealth(
            String upstreamStatus,
            String message,
            String fallbackMessage
    ) {
        HealthState state = switch (upstreamStatus == null ? "" : upstreamStatus) {
            case "ok" -> HealthState.UP;
            case "error" -> HealthState.DOWN;
            default -> HealthState.UNKNOWN;
        };
        return new ComponentHealth(state, message == null ? fallbackMessage : message);
    }

    private static ModelHealth modelHealth(Object payload, String configuredModel) {
        Map<?, ?> modelPayload = mapValue(payload);
        String model = stringValue(modelPayload.get("model"));
        if (model == null || model.isBlank()) {
            model = configuredModel;
        }
        Boolean available = booleanValue(modelPayload.get("available"));
        HealthState state = available == null
                ? HealthState.UNKNOWN
                : available ? HealthState.UP : HealthState.DOWN;
        String message = stringValue(modelPayload.get("message"));
        if (message == null) {
            message = "Python AI service did not report model availability.";
        }
        return new ModelHealth(model, state, available, message);
    }

    private static ModelHealth unknownModel(String model, String message) {
        return new ModelHealth(model, HealthState.UNKNOWN, null, message);
    }

    private static Map<String, Object> bodyOf(ResponseEntity<Map<String, Object>> response) {
        return response.getBody() == null ? Map.of() : response.getBody();
    }

    private static Map<?, ?> mapValue(Object value) {
        return value instanceof Map<?, ?> map ? map : Map.of();
    }

    private static String stringValue(Object value) {
        return value instanceof String string ? string : null;
    }

    private static Boolean booleanValue(Object value) {
        return value instanceof Boolean booleanResult ? booleanResult : null;
    }

    private static String pythonFailureMessage(
            ResponseEntity<Map<String, Object>> response,
            Map<String, Object> body
    ) {
        String detail = stringValue(body.get("detail"));
        return "Python AI service health check returned HTTP "
                + response.getStatusCode().value()
                + (detail == null ? "." : ": " + detail);
    }

    private static String messageOf(RestClientException exception) {
        return exception.getMessage() == null
                ? exception.getClass().getSimpleName()
                : exception.getMessage();
    }
}
