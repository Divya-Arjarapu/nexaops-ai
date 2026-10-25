package com.nexaops.backend.documents;

import java.util.UUID;

public record DocumentEmbeddingResponse(
        UUID documentId,
        String embeddingModel,
        int totalChunkCount,
        int newlyEmbeddedChunkCount,
        int alreadyEmbeddedChunkCount,
        int failedChunkCount,
        String status,
        String message
) {
}
