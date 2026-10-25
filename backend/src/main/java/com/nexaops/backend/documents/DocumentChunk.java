package com.nexaops.backend.documents;

public record DocumentChunk(
        int chunkIndex,
        String content,
        Integer pageNumber,
        String sourceLocator,
        Integer sourceStartOffset,
        Integer sourceEndOffset
) {
}
