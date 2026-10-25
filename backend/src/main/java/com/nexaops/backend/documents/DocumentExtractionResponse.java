package com.nexaops.backend.documents;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

public record DocumentExtractionResponse(
        String filename,
        @JsonProperty("media_type") String mediaType,
        List<ExtractedPage> pages,
        @JsonProperty("character_count") Integer characterCount,
        @JsonProperty("page_count") Integer pageCount
) {
}
