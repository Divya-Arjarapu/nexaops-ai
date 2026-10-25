package com.nexaops.backend.documents;

import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/documents")
public class DocumentEmbeddingController {

    private final DocumentEmbeddingService embeddingService;

    public DocumentEmbeddingController(DocumentEmbeddingService embeddingService) {
        this.embeddingService = embeddingService;
    }

    @PostMapping("/{documentId}/embeddings")
    public ResponseEntity<DocumentEmbeddingResponse> generateEmbeddings(
            @PathVariable UUID documentId
    ) {
        return ResponseEntity.ok(embeddingService.generate(documentId));
    }
}
