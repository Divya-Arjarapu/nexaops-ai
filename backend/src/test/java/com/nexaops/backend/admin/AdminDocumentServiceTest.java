package com.nexaops.backend.admin;

import java.util.List;
import java.util.UUID;
import java.time.OffsetDateTime;
import java.sql.ResultSet;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AdminDocumentServiceTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final AdminDocumentService service = new AdminDocumentService(jdbcTemplate);

    @Test
    void listingUsesOnlyMetadataAndAggregatesChunkCounts() throws Exception {
        when(jdbcTemplate.query(anyString(), org.mockito.ArgumentMatchers.<RowMapper<ManagedDocument>>any()))
                .thenReturn(List.of());
        assertEquals(List.of(), service.list());
        org.mockito.ArgumentCaptor<String> sql = org.mockito.ArgumentCaptor.forClass(String.class);
        org.mockito.ArgumentCaptor<RowMapper<ManagedDocument>> mapper =
                org.mockito.ArgumentCaptor.forClass(RowMapper.class);
        verify(jdbcTemplate).query(sql.capture(), mapper.capture());
        assertTrue(sql.getValue().contains("embedded_chunk_count"));
        assertTrue(sql.getValue().contains("GROUP BY"));
        assertFalse(sql.getValue().contains("c.content"));

        UUID id = UUID.randomUUID();
        OffsetDateTime uploadedAt = OffsetDateTime.parse("2026-10-24T12:30:00Z");
        ResultSet row = mock(ResultSet.class);
        when(row.getObject("id", UUID.class)).thenReturn(id);
        when(row.getString("original_filename")).thenReturn("policy.txt");
        when(row.getObject("created_at", OffsetDateTime.class)).thenReturn(uploadedAt);
        when(row.getString("status")).thenReturn("ready");
        when(row.getInt("chunk_count")).thenReturn(4);
        when(row.getInt("embedded_chunk_count")).thenReturn(3);
        ManagedDocument mapped = mapper.getValue().mapRow(row, 0);
        assertEquals(new ManagedDocument(id, "policy.txt", uploadedAt, "ready", 4, 3), mapped);
    }

    @Test
    void deletesOnlyRequestedDocumentAndReportsUnknownId() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(id))).thenReturn(1);
        service.delete(id);
        verify(jdbcTemplate).update("DELETE FROM nexaops.documents WHERE id = ?", id);

        when(jdbcTemplate.update(anyString(), eq(id))).thenReturn(0);
        AdminDocumentException exception = assertThrows(AdminDocumentException.class, () -> service.delete(id));
        assertEquals(HttpStatus.NOT_FOUND, exception.status());
    }

    @Test
    void databaseFailuresReturnSafeServiceError() {
        UUID id = UUID.randomUUID();
        when(jdbcTemplate.update(anyString(), eq(id))).thenThrow(new DataAccessResourceFailureException("private db details"));
        AdminDocumentException exception = assertThrows(AdminDocumentException.class, () -> service.delete(id));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, exception.status());
        assertFalse(exception.getMessage().contains("private db details"));
    }
}
