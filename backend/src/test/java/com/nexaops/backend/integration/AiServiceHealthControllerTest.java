package com.nexaops.backend.integration;

import com.nexaops.backend.integration.AiIntegrationHealthResponse.ComponentHealth;
import com.nexaops.backend.integration.AiIntegrationHealthResponse.HealthState;
import com.nexaops.backend.integration.AiIntegrationHealthResponse.ModelHealth;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AiServiceHealthController.class)
class AiServiceHealthControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AiServiceHealthService aiServiceHealthService;

    @Test
    void integrationHealthEndpointReturnsStructuredComponentStatuses() throws Exception {
        when(aiServiceHealthService.checkHealth()).thenReturn(new AiIntegrationHealthResponse(
                new ComponentHealth(HealthState.UP, "Java backend is responding."),
                new ComponentHealth(HealthState.UP, "Python AI service is reachable."),
                new ComponentHealth(HealthState.UP, "Ollama server is reachable."),
                new ModelHealth("qwen3:4b", HealthState.UP, true, "Model is installed."),
                new ModelHealth("nomic-embed-text", HealthState.UP, true, "Model is installed.")
        ));

        mockMvc.perform(get("/api/ai/health").accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.javaBackend.status").value("UP"))
                .andExpect(jsonPath("$.pythonAiService.status").value("UP"))
                .andExpect(jsonPath("$.ollama.status").value("UP"))
                .andExpect(jsonPath("$.generationModel.available").value(true))
                .andExpect(jsonPath("$.embeddingModel.available").value(true));
    }
}
