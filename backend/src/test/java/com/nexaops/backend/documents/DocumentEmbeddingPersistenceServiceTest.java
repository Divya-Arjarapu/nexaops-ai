package com.nexaops.backend.documents;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentEmbeddingPersistenceServiceTest {

    @Test
    void persistsAsBoundVectorParametersAndNeverInterpolatesVectorIntoSql() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentEmbeddingPersistenceService service = new DocumentEmbeddingPersistenceService(jdbc);
        UUID documentId = UUID.randomUUID();
        PendingDocumentChunk chunk = new PendingDocumentChunk(41, 0, "a chunk");
        List<Double> vector = List.of(0.25, -0.5);
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        int saved = service.storeEmbeddings(documentId, "nomic-embed-text", List.of(chunk), List.of(vector));

        assertEquals(1, saved);
        verify(jdbc).update(eq("""
                        UPDATE nexaops.document_chunks
                        SET embedding = ?::public.vector, embedding_model = ?
                        WHERE id = ? AND document_id = ? AND embedding IS NULL
                        """), eq(new Object[]{"[0.25,-0.5]", "nomic-embed-text", 41L, documentId}));
    }

    @Test
    void rejectsNonFiniteVectorValuesBeforeDatabaseUpdate() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentEmbeddingPersistenceService service = new DocumentEmbeddingPersistenceService(jdbc);

        assertThrows(IllegalArgumentException.class,
                () -> service.storeEmbeddings(UUID.randomUUID(), "nomic-embed-text",
                        List.of(new PendingDocumentChunk(1, 0, "text")), List.of(List.of(Double.NaN))));
    }

    @Test
    void reportsPersistenceFailureWithoutExposingDatabaseDetails() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        DocumentEmbeddingPersistenceService service = new DocumentEmbeddingPersistenceService(jdbc);
        when(jdbc.update(anyString(), any(Object[].class)))
                .thenThrow(new DataAccessResourceFailureException("synthetic database detail"));

        DocumentUploadException error = assertThrows(DocumentUploadException.class,
                () -> service.storeEmbeddings(UUID.randomUUID(), "nomic-embed-text",
                        List.of(new PendingDocumentChunk(1, 0, "text")), List.of(List.of(0.1))));

        assertEquals(HttpStatus.INTERNAL_SERVER_ERROR, error.status());
        assertEquals("Embeddings could not be saved. No partial embedding update was retained.", error.getMessage());
    }
}
