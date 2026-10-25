package com.nexaops.backend.rag;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonAlias;

public record RagAnswerResponse(
        String query,
        String answer,
        @JsonAlias("has_context") boolean hasContext,
        List<RagCitation> citations,
        List<RagSource> sources
) {
}
