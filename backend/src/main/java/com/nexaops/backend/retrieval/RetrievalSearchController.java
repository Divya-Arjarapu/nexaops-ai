package com.nexaops.backend.retrieval;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/retrieval")
public class RetrievalSearchController {

    private final RetrievalSearchService retrievalService;

    public RetrievalSearchController(RetrievalSearchService retrievalService) {
        this.retrievalService = retrievalService;
    }

    @PostMapping(value = "/search", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RetrievalSearchResponse> search(@RequestBody RetrievalSearchRequest request) {
        return ResponseEntity.ok(retrievalService.search(request));
    }
}
