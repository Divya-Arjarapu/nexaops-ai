package com.nexaops.backend.retrieval;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RetrievalSearchResult(
        @JsonAlias("document_id") String documentId,
        String filename,
        @JsonAlias("chunk_id") String chunkId,
        @JsonAlias("chunk_index") int chunkIndex,
        @JsonAlias("page_number") Integer pageNumber,
        @JsonAlias("section_title") String sectionTitle,
        @JsonAlias("source_locator") String sourceLocator,
        String text,
        @JsonAlias("similarity_score") double similarityScore
) {
}
