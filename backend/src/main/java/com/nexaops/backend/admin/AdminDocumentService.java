package com.nexaops.backend.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdminDocumentService {

    private final JdbcTemplate jdbcTemplate;

    public AdminDocumentService(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<ManagedDocument> list() {
        try {
            return jdbcTemplate.query("""
                    SELECT d.id, d.original_filename, d.created_at, d.status,
                           count(c.id) AS chunk_count,
                           count(c.id) FILTER (WHERE c.embedding IS NOT NULL) AS embedded_chunk_count
                    FROM nexaops.documents d
                    LEFT JOIN nexaops.document_chunks c ON c.document_id = d.id
                    GROUP BY d.id, d.original_filename, d.created_at, d.status
                    ORDER BY d.created_at DESC, d.id
                    """, (result, row) -> new ManagedDocument(
                    result.getObject("id", UUID.class),
                    result.getString("original_filename"),
                    result.getObject("created_at", java.time.OffsetDateTime.class),
                    result.getString("status"),
                    result.getInt("chunk_count"),
                    result.getInt("embedded_chunk_count")
            ));
        } catch (DataAccessException exception) {
            throw new AdminDocumentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The document list could not be loaded. Please try again.");
        }
    }

    @Transactional
    public void delete(UUID documentId) {
        try {
            int deleted = jdbcTemplate.update("DELETE FROM nexaops.documents WHERE id = ?", documentId);
            if (deleted == 0) {
                throw new AdminDocumentException(HttpStatus.NOT_FOUND, "The requested document was not found.");
            }
        } catch (DataAccessException exception) {
            throw new AdminDocumentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The document could not be removed. No success was reported; please try again.");
        }
    }
}
