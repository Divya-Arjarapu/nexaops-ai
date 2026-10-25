package com.nexaops.backend.documents;

import java.util.List;

public record EmbeddingGenerationResponse(String model, List<List<Double>> embeddings) {
}
