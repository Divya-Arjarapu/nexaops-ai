package com.nexaops.backend.retrieval;

import java.util.List;
import com.fasterxml.jackson.annotation.JsonAlias;

public record RetrievalSearchResponse(
        String query,
        List<RetrievalSearchResult> results,
        @JsonAlias("result_count") int resultCount
) {
}
