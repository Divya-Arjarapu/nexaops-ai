package com.nexaops.backend.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEmbeddingServiceTest {

    private static final String MODEL = "nomic-embed-text";
    private final DocumentEmbeddingPersistenceService persistence = mock(DocumentEmbeddingPersistenceService.class);
    private final DocumentEmbeddingClient client = mock(DocumentEmbeddingClient.class);
    private final DocumentEmbeddingService service = new DocumentEmbeddingService(persistence, client, MODEL, 1);

    @Test
    void embedsOnlyPendingChunksInOrderAndPersistsAfterAllBatchesValidate() {
        UUID documentId = UUID.randomUUID();
        List<PendingDocumentChunk> chunks = List.of(
                new PendingDocumentChunk(21, 1, "first"),
                new PendingDocumentChunk(22, 2, "second")
        );
        when(persistence.loadWork(documentId)).thenReturn(new DocumentEmbeddingWork(true, 3, 1, chunks));
        when(client.generate(MODEL, List.of("first"))).thenReturn(response(vector(1.0)));
        when(client.generate(MODEL, List.of("second"))).thenReturn(response(vector(2.0)));
        when(persistence.storeEmbeddings(eq(documentId), eq(MODEL), eq(chunks), anyList())).thenReturn(2);

        DocumentEmbeddingResponse result = service.generate(documentId);

        assertEquals(documentId, result.documentId());
        assertEquals(3, result.totalChunkCount());
        assertEquals(2, result.newlyEmbeddedChunkCount());
        assertEquals(1, result.alreadyEmbeddedChunkCount());
        assertEquals(0, result.failedChunkCount());
        assertEquals("complete", result.status());
        ArgumentCaptor<List<List<Double>>> vectors = ArgumentCaptor.forClass(List.class);
        InOrder order = inOrder(client, persistence);
        order.verify(client).generate(MODEL, List.of("first"));
        order.verify(client).generate(MODEL, List.of("second"));
        order.verify(persistence).storeEmbeddings(eq(documentId), eq(MODEL), eq(chunks), vectors.capture());
        assertEquals(1.0, vectors.getValue().get(0).get(0));
        assertEquals(2.0, vectors.getValue().get(1).get(0));
    }

    @Test
    void returnsIdempotentResultWithoutInferenceWhenAllChunksAreAlreadyEmbedded() {
        UUID documentId = UUID.randomUUID();
        when(persistence.loadWork(documentId)).thenReturn(new DocumentEmbeddingWork(true, 4, 4, List.of()));

        DocumentEmbeddingResponse result = service.generate(documentId);

        assertEquals(0, result.newlyEmbeddedChunkCount());
        assertEquals(4, result.alreadyEmbeddedChunkCount());
        verify(client, never()).generate(any(), anyList());
        verify(persistence, never()).storeEmbeddings(any(), any(), anyList(), anyList());
    }

    @Test
    void missingDocumentAndInvalidVectorResponsesDoNotWrite() {
        UUID missingId = UUID.randomUUID();
        when(persistence.loadWork(missingId)).thenReturn(new DocumentEmbeddingWork(false, 0, 0, List.of()));
        assertEquals(HttpStatus.NOT_FOUND, assertThrows(DocumentUploadException.class,
                () -> service.generate(missingId)).status());

        UUID badId = UUID.randomUUID();
        List<PendingDocumentChunk> chunks = List.of(new PendingDocumentChunk(1, 0, "text"));
        when(persistence.loadWork(badId)).thenReturn(new DocumentEmbeddingWork(true, 1, 0, chunks));
        when(client.generate(MODEL, List.of("text"))).thenReturn(
                new EmbeddingGenerationResponse(MODEL, List.of(List.of(0.1, 0.2)))
        );
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(DocumentUploadException.class,
                () -> service.generate(badId)).status());
        verify(persistence, never()).storeEmbeddings(eq(badId), any(), anyList(), anyList());
    }

    @Test
    void laterBatchFailureDoesNotPersistEarlierVectors() {
        UUID documentId = UUID.randomUUID();
        List<PendingDocumentChunk> chunks = List.of(
                new PendingDocumentChunk(1, 0, "first"),
                new PendingDocumentChunk(2, 1, "second")
        );
        when(persistence.loadWork(documentId)).thenReturn(new DocumentEmbeddingWork(true, 2, 0, chunks));
        when(client.generate(MODEL, List.of("first"))).thenReturn(response(vector(1.0)));
        when(client.generate(MODEL, List.of("second"))).thenThrow(
                new DocumentUploadException(HttpStatus.SERVICE_UNAVAILABLE, "Ollama unavailable.")
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(DocumentUploadException.class,
                () -> service.generate(documentId)).status());
        verify(persistence, never()).storeEmbeddings(any(), any(), anyList(), anyList());
    }

    private static EmbeddingGenerationResponse response(List<Double> vector) {
        return new EmbeddingGenerationResponse(MODEL, List.of(vector));
    }

    private static List<Double> vector(double first) {
        List<Double> vector = new ArrayList<>(DocumentEmbeddingService.VECTOR_DIMENSIONS);
        vector.add(first);
        for (int index = 1; index < DocumentEmbeddingService.VECTOR_DIMENSIONS; index++) vector.add(0.0);
        return List.copyOf(vector);
    }
}
