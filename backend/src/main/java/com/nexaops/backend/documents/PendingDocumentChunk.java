package com.nexaops.backend.documents;

public record PendingDocumentChunk(long id, int chunkIndex, String content) {
}
