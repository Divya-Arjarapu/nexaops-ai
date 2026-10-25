package com.nexaops.backend.documents;

import com.fasterxml.jackson.annotation.JsonProperty;

public record ExtractedPage(
        @JsonProperty("page_number") Integer pageNumber,
        String text
) {
}
