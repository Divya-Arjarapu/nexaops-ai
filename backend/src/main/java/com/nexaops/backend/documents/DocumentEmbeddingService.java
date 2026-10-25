package com.nexaops.backend.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

@Service
public class DocumentEmbeddingService {

    public static final int VECTOR_DIMENSIONS = 768;

    private final DocumentEmbeddingPersistenceService persistenceService;
    private final DocumentEmbeddingClient embeddingClient;
    private final String embeddingModel;
    private final int batchSize;

    public DocumentEmbeddingService(
            DocumentEmbeddingPersistenceService persistenceService,
            DocumentEmbeddingClient embeddingClient,
            @Value("${nexaops.ai-service.embedding-model}") String embeddingModel,
            @Value("${nexaops.ai-service.embedding-batch-size}") int batchSize
    ) {
        if (batchSize < 1) throw new IllegalArgumentException("Embedding batch size must be positive.");
        this.persistenceService = persistenceService;
        this.embeddingClient = embeddingClient;
        this.embeddingModel = embeddingModel;
        this.batchSize = batchSize;
    }

    public DocumentEmbeddingResponse generate(UUID documentId) {
        DocumentEmbeddingWork work = persistenceService.loadWork(documentId);
        if (!work.documentExists()) {
            throw new DocumentUploadException(HttpStatus.NOT_FOUND, "The requested document does not exist.");
        }

        List<PendingDocumentChunk> pending = work.pendingChunks();
        if (pending.isEmpty()) {
            return new DocumentEmbeddingResponse(
                    documentId, embeddingModel, work.totalChunkCount(), 0,
                    work.totalChunkCount(), 0, "complete", "All document chunks already have embeddings."
            );
        }

        List<List<Double>> embeddings = new ArrayList<>(pending.size());
        for (int start = 0; start < pending.size(); start += batchSize) {
            int end = Math.min(start + batchSize, pending.size());
            List<PendingDocumentChunk> batch = pending.subList(start, end);
            EmbeddingGenerationResponse response = embeddingClient.generate(
                    embeddingModel,
                    batch.stream().map(PendingDocumentChunk::content).toList()
            );
            embeddings.addAll(validateResponse(response, batch.size()));
        }

        // No vectors are written unless every requested batch passed validation.
        int newlyEmbedded = persistenceService.storeEmbeddings(documentId, embeddingModel, pending, embeddings);
        int alreadyEmbedded = work.totalChunkCount() - newlyEmbedded;
        return new DocumentEmbeddingResponse(
                documentId,
                embeddingModel,
                work.totalChunkCount(),
                newlyEmbedded,
                alreadyEmbedded,
                0,
                "complete",
                "Embeddings are stored for all document chunks."
        );
    }

    private List<List<Double>> validateResponse(EmbeddingGenerationResponse response, int expectedCount) {
        if (!embeddingModel.equals(response.model())) {
            throw invalidResponse("The embedding service returned a different model.");
        }
        if (response.embeddings().size() != expectedCount) {
            throw invalidResponse("The embedding service returned an unexpected number of vectors.");
        }
        List<List<Double>> checked = new ArrayList<>(expectedCount);
        for (List<Double> vector : response.embeddings()) {
            if (vector == null || vector.size() != VECTOR_DIMENSIONS) {
                throw invalidResponse("The embedding service returned a vector with an invalid dimension.");
            }
            if (vector.stream().anyMatch(value -> value == null || !Double.isFinite(value))) {
                throw invalidResponse("The embedding service returned non-finite vector values.");
            }
            checked.add(List.copyOf(vector));
        }
        return List.copyOf(checked);
    }

    private static DocumentUploadException invalidResponse(String message) {
        return new DocumentUploadException(HttpStatus.BAD_GATEWAY, message);
    }
}
