package com.nexaops.backend.integration;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/ai")
public class AiServiceHealthController {

    private final AiServiceHealthService aiServiceHealthService;

    public AiServiceHealthController(AiServiceHealthService aiServiceHealthService) {
        this.aiServiceHealthService = aiServiceHealthService;
    }

    @GetMapping("/health")
    public AiIntegrationHealthResponse health() {
        return aiServiceHealthService.checkHealth();
    }
}
