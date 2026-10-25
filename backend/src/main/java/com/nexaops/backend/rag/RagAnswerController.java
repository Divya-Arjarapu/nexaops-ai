package com.nexaops.backend.rag;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/rag")
public class RagAnswerController {

    private final RagAnswerService ragAnswerService;

    public RagAnswerController(RagAnswerService ragAnswerService) {
        this.ragAnswerService = ragAnswerService;
    }

    @PostMapping(value = "/answer", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RagAnswerResponse> answer(@RequestBody RagAnswerRequest request) {
        return ResponseEntity.ok(ragAnswerService.answer(request));
    }
}
