package com.nexaops.backend.rag;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RagAnswerRequest(
        String query,
        @JsonProperty("top_k") Integer topK
) {
}
