package com.nexaops.backend.documents;

import java.util.List;

public record EmbeddingGenerationRequest(List<String> texts) {
}
