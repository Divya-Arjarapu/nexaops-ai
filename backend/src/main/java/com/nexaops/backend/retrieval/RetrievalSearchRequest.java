package com.nexaops.backend.retrieval;

import com.fasterxml.jackson.annotation.JsonProperty;

public record RetrievalSearchRequest(
        String query,
        @JsonProperty("top_k") Integer topK
) {
}
