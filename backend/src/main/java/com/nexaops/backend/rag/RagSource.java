package com.nexaops.backend.rag;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RagSource(
        @JsonAlias("source_id") String sourceId,
        @JsonAlias("document_id") String documentId,
        String filename,
        @JsonAlias("chunk_id") String chunkId,
        @JsonAlias("chunk_index") int chunkIndex,
        @JsonAlias("page_number") Integer pageNumber,
        @JsonAlias("section_title") String sectionTitle,
        @JsonAlias("source_locator") String sourceLocator,
        @JsonAlias("similarity_score") double similarityScore,
        String text
) {
}
