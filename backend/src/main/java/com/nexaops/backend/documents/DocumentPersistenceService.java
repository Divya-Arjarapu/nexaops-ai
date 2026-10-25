package com.nexaops.backend.documents;

import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DocumentPersistenceService {

    private static final String INSERT_CHUNK = """
            INSERT INTO nexaops.document_chunks (
                document_id, chunk_index, content, page_number, source_locator,
                source_start_offset, source_end_offset
            ) VALUES (?, ?, ?, ?, ?, ?, ?)
            """;

    private final JdbcTemplate jdbcTemplate;

    public DocumentPersistenceService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Transactional
    public UUID store(ValidatedDocument document, List<DocumentChunk> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "The document contains no extractable text.");
        }

        try {
            UUID documentId = jdbcTemplate.queryForObject("""
                            INSERT INTO nexaops.documents (
                                title, original_filename, media_type, source_uri,
                                content_sha256, file_size_bytes, status, status_message
                            ) VALUES (?, ?, ?, NULL, ?, ?, 'ready', 'Text extracted; embeddings are pending.')
                            RETURNING id
                            """,
                    UUID.class,
                    document.filename(),
                    document.filename(),
                    document.mediaType(),
                    document.sha256(),
                    (long) document.content().length
            );

            if (documentId == null) throw new IllegalStateException("PostgreSQL returned no document identifier.");
            jdbcTemplate.batchUpdate(INSERT_CHUNK, chunks, 100, bindChunk(documentId));
            return documentId;
        } catch (DataAccessException exception) {
            throw new DocumentUploadException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "The document could not be saved. No partial document record was retained.",
                    exception
            );
        }
    }

    private org.springframework.jdbc.core.ParameterizedPreparedStatementSetter<DocumentChunk> bindChunk(UUID documentId) {
        return (PreparedStatement statement, DocumentChunk chunk) -> {
            statement.setObject(1, documentId);
            statement.setInt(2, chunk.chunkIndex());
            statement.setString(3, chunk.content());
            if (chunk.pageNumber() == null) statement.setNull(4, Types.INTEGER);
            else statement.setInt(4, chunk.pageNumber());
            statement.setString(5, chunk.sourceLocator());
            if (chunk.sourceStartOffset() == null) statement.setNull(6, Types.INTEGER);
            else statement.setInt(6, chunk.sourceStartOffset());
            if (chunk.sourceEndOffset() == null) statement.setNull(7, Types.INTEGER);
            else statement.setInt(7, chunk.sourceEndOffset());
        };
    }
}
