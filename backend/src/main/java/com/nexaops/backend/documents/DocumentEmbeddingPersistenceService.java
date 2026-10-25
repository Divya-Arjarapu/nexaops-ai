package com.nexaops.backend.documents;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentEmbeddingPersistenceService {

    private static final String UPDATE_EMBEDDING = """
            UPDATE nexaops.document_chunks
            SET embedding = ?::public.vector, embedding_model = ?
            WHERE id = ? AND document_id = ? AND embedding IS NULL
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentEmbeddingPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public DocumentEmbeddingWork loadWork(UUID documentId) {
        Boolean exists = jdbcTemplate.queryForObject(
                "SELECT EXISTS (SELECT 1 FROM nexaops.documents WHERE id = ?)",
                Boolean.class,
                documentId
        );
        if (!Boolean.TRUE.equals(exists)) {
            return new DocumentEmbeddingWork(false, 0, 0, List.of());
        }

        Integer total = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nexaops.document_chunks WHERE document_id = ?",
                Integer.class,
                documentId
        );
        Integer embedded = jdbcTemplate.queryForObject(
                "SELECT count(*) FROM nexaops.document_chunks WHERE document_id = ? AND embedding IS NOT NULL",
                Integer.class,
                documentId
        );
        List<PendingDocumentChunk> pending = jdbcTemplate.query(
                "SELECT id, chunk_index, content FROM nexaops.document_chunks WHERE document_id = ? AND embedding IS NULL ORDER BY chunk_index",
                (result, row) -> new PendingDocumentChunk(
                        result.getLong("id"), result.getInt("chunk_index"), result.getString("content")
                ),
                documentId
        );
        return new DocumentEmbeddingWork(
                true,
                total == null ? 0 : total,
                embedded == null ? 0 : embedded,
                List.copyOf(pending)
        );
    }

    @Transactional
    public int storeEmbeddings(
            UUID documentId,
            String model,
            List<PendingDocumentChunk> chunks,
            List<List<Double>> embeddings
    ) {
        if (chunks.size() != embeddings.size()) {
            throw new IllegalArgumentException("Embedding count must match pending chunk count.");
        }
        int newlyEmbedded = 0;
        try {
            for (int index = 0; index < chunks.size(); index++) {
                PendingDocumentChunk chunk = chunks.get(index);
                String vector = vectorLiteral(embeddings.get(index));
                int updated = jdbcTemplate.update(
                        UPDATE_EMBEDDING, vector, model, chunk.id(), documentId
                );
                if (updated == 1) {
                    newlyEmbedded++;
                } else {
                    Boolean existsAndEmbedded = jdbcTemplate.queryForObject(
                            "SELECT embedding IS NOT NULL FROM nexaops.document_chunks WHERE id = ? AND document_id = ?",
                            Boolean.class,
                            chunk.id(),
                            documentId
                    );
                    if (!Boolean.TRUE.equals(existsAndEmbedded)) {
                        throw new DocumentUploadException(
                                HttpStatus.CONFLICT,
                                "The document changed while embeddings were being generated. Retry the operation."
                        );
                    }
                }
            }
            return newlyEmbedded;
        } catch (DataAccessException exception) {
            throw new DocumentUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Embeddings could not be saved. No partial embedding update was retained.",
                    exception
            );
        }
    }

    static String vectorLiteral(List<Double> vector) {
        List<String> values = new ArrayList<>(vector.size());
        for (Double value : vector) {
            if (value == null || !Double.isFinite(value)) {
                throw new IllegalArgumentException("Embedding values must be finite numbers.");
            }
            values.add(Double.toString(value));
        }
        return "[" + String.join(",", values) + "]";
    }
}
