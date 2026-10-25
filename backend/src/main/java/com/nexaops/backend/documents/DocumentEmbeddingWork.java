package com.nexaops.backend.documents;

import java.util.List;

public record DocumentEmbeddingWork(
        boolean documentExists,
        int totalChunkCount,
        int alreadyEmbeddedChunkCount,
        List<PendingDocumentChunk> pendingChunks
) {
}
