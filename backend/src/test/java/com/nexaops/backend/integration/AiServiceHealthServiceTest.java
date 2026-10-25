package com.nexaops.backend.integration;

import java.io.IOException;
import java.net.SocketTimeoutException;

import com.nexaops.backend.integration.AiIntegrationHealthResponse.HealthState;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class AiServiceHealthServiceTest {

    private static final String AI_SERVICE_URL = "http://127.0.0.1:8001";

    private MockRestServiceServer mockServer;
    private AiServiceHealthService service;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        service = new AiServiceHealthService(
                builder.baseUrl(AI_SERVICE_URL).build(),
                "qwen3:4b",
                "nomic-embed-text"
        );
    }

    @Test
    void reportsAllComponentsAndModelsAvailable() {
        expectPythonHealth();
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health/ollama"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(ollamaDiagnostics(), MediaType.APPLICATION_JSON));

        AiIntegrationHealthResponse result = service.checkHealth();

        assertEquals(HealthState.UP, result.javaBackend().status());
        assertEquals(HealthState.UP, result.pythonAiService().status());
        assertEquals(HealthState.UP, result.ollama().status());
        assertEquals(Boolean.TRUE, result.generationModel().available());
        assertEquals(Boolean.TRUE, result.embeddingModel().available());
        mockServer.verify();
    }

    @Test
    void reportsPythonConnectionFailureWithoutFailingTheBackend() {
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new IOException("connection refused")));

        AiIntegrationHealthResponse result = service.checkHealth();

        assertEquals(HealthState.UP, result.javaBackend().status());
        assertEquals(HealthState.DOWN, result.pythonAiService().status());
        assertEquals(HealthState.UNKNOWN, result.ollama().status());
        assertEquals(null, result.generationModel().available());
        assertEquals(null, result.embeddingModel().available());
        mockServer.verify();
    }

    @Test
    void reportsPythonTimeoutGracefully() {
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withException(new SocketTimeoutException("Read timed out")));

        AiIntegrationHealthResponse result = service.checkHealth();

        assertEquals(HealthState.UP, result.javaBackend().status());
        assertEquals(HealthState.DOWN, result.pythonAiService().status());
        assertEquals(HealthState.UNKNOWN, result.ollama().status());
        mockServer.verify();
    }

    @Test
    void reportsOllamaUnavailableWhilePythonRemainsAvailable() {
        expectPythonHealth();
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health/ollama"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "status": "degraded",
                                  "ollama": {"status": "error", "message": "Ollama is unreachable."},
                                  "generation_model": {"status": "error", "model": "qwen3:4b", "available": null, "message": "Not checked."},
                                  "embedding_model": {"status": "error", "model": "nomic-embed-text", "available": null, "message": "Not checked."}
                                }
                                """));

        AiIntegrationHealthResponse result = service.checkHealth();

        assertEquals(HealthState.UP, result.pythonAiService().status());
        assertEquals(HealthState.DOWN, result.ollama().status());
        assertEquals(HealthState.UNKNOWN, result.generationModel().status());
        assertEquals(HealthState.UNKNOWN, result.embeddingModel().status());
        mockServer.verify();
    }

    @Test
    void preservesMissingModelDiagnostic() {
        expectPythonHealth();
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health/ollama"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {
                                  "status": "degraded",
                                  "ollama": {"status": "ok", "message": "Ollama server is reachable."},
                                  "generation_model": {"status": "error", "model": "qwen3:4b", "available": false, "message": "Model 'qwen3:4b' is not installed. Install it manually with `ollama pull qwen3:4b`."},
                                  "embedding_model": {"status": "ok", "model": "nomic-embed-text", "available": true, "message": "Model is installed."}
                                }
                                """));

        AiIntegrationHealthResponse result = service.checkHealth();

        assertEquals(HealthState.UP, result.ollama().status());
        assertEquals(HealthState.DOWN, result.generationModel().status());
        assertEquals(Boolean.FALSE, result.generationModel().available());
        assertEquals(HealthState.UP, result.embeddingModel().status());
        assertEquals(Boolean.TRUE, result.embeddingModel().available());
        mockServer.verify();
    }

    private void expectPythonHealth() {
        mockServer.expect(requestTo(AI_SERVICE_URL + "/health"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(
                        "{\"status\":\"ok\",\"service\":\"nexaops-ai-service\"}",
                        MediaType.APPLICATION_JSON
                ));
    }

    private static String ollamaDiagnostics() {
        return """
                {
                  "status": "ok",
                  "ollama": {"status": "ok", "message": "Ollama server is reachable.", "version": "0.40.10"},
                  "generation_model": {"status": "ok", "model": "qwen3:4b", "available": true, "message": "Model is installed."},
                  "embedding_model": {"status": "ok", "model": "nomic-embed-text", "available": true, "message": "Model is installed."}
                }
                """;
    }
}
