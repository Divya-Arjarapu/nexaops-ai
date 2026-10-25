package com.nexaops.backend.documents;

import java.util.UUID;

public record DocumentUploadResponse(
        UUID documentId,
        String filename,
        String status,
        int characterCount,
        Integer pageCount,
        int chunkCount,
        int embeddingCount,
        String message
) {
}
