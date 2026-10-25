package com.nexaops.backend.documents;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadServiceTest {

    @Test
    void repeatedUploadsOfTheSameFilenameCreateSeparateDocuments() {
        DocumentExtractionClient extractor = mock(DocumentExtractionClient.class);
        DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
        when(extractor.extract(any())).thenReturn(new DocumentExtractionResponse(
                "notes.txt", "text/plain", List.of(new ExtractedPage(null, "A short document body.")), 22, null
        ));
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(persistence.store(any(), anyList())).thenReturn(firstId, secondId);
        DocumentUploadService service = new DocumentUploadService(
                new DocumentUploadValidator(1024), extractor, new DocumentChunker(20, 3), persistence
        );
        MockMultipartFile file = new MockMultipartFile("file", "notes.txt", "text/plain", "A short document body.".getBytes());

        DocumentUploadResponse first = service.upload(file);
        DocumentUploadResponse second = service.upload(file);

        assertNotEquals(first.documentId(), second.documentId());
        assertEquals(firstId, first.documentId());
        assertEquals(secondId, second.documentId());
        verify(persistence, times(2)).store(any(), anyList());
    }

    @Test
    void extractsChunksAndReturnsStructuredReadyResponse() {
        DocumentUploadValidator validator = new DocumentUploadValidator(1024);
        DocumentExtractionClient extractor = mock(DocumentExtractionClient.class);
        DocumentChunker chunker = new DocumentChunker(20, 3);
        DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
        when(extractor.extract(any())).thenReturn(new DocumentExtractionResponse(
                "notes.txt", "text/plain", List.of(new ExtractedPage(null, "A short document body.")), 22, null
        ));
        UUID id = UUID.randomUUID();
        when(persistence.store(any(), anyList())).thenReturn(id);

        DocumentUploadResponse response = new DocumentUploadService(validator, extractor, chunker, persistence)
                .upload(new MockMultipartFile("file", "notes.txt", "text/plain", "A short document body.".getBytes()));

        assertEquals(id, response.documentId());
        assertEquals("ready", response.status());
        assertEquals(22, response.characterCount());
        assertEquals(0, response.embeddingCount());
        verify(persistence).store(any(), anyList());
    }

    @Test
    void rejectsUnsupportedFilesBeforeCallingPython() {
        DocumentExtractionClient extractor = mock(DocumentExtractionClient.class);
        DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
        DocumentUploadService service = new DocumentUploadService(
                new DocumentUploadValidator(100), extractor, new DocumentChunker(20, 2), persistence
        );

        DocumentUploadException error = assertThrows(DocumentUploadException.class,
                () -> service.upload(new MockMultipartFile("file", "archive.zip", "application/zip", new byte[]{1})));

        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, error.status());
        verify(extractor, never()).extract(any());
        verify(persistence, never()).store(any(), anyList());
    }

    @Test
    void extractionFailureDoesNotPersistAnyDocument() {
        DocumentExtractionClient extractor = mock(DocumentExtractionClient.class);
        DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
        when(extractor.extract(any())).thenThrow(new DocumentUploadException(HttpStatus.SERVICE_UNAVAILABLE, "Extraction service offline."));
        DocumentUploadService service = new DocumentUploadService(
                new DocumentUploadValidator(100), extractor, new DocumentChunker(20, 2), persistence
        );

        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(DocumentUploadException.class,
                () -> service.upload(new MockMultipartFile("file", "notes.txt", "text/plain", "body".getBytes()))).status());
        verify(persistence, never()).store(any(), anyList());
    }

    @Test
    void refusesEmptyExtractionBeforePersistence() {
        DocumentExtractionClient extractor = mock(DocumentExtractionClient.class);
        DocumentPersistenceService persistence = mock(DocumentPersistenceService.class);
        when(extractor.extract(any())).thenReturn(new DocumentExtractionResponse(
                "empty.txt", "text/plain", List.of(new ExtractedPage(null, "  \n ")), 4, null
        ));
        DocumentUploadService service = new DocumentUploadService(
                new DocumentUploadValidator(100), extractor, new DocumentChunker(20, 2), persistence
        );

        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(DocumentUploadException.class,
                () -> service.upload(new MockMultipartFile("file", "empty.txt", "text/plain", "body".getBytes()))).status());
        verify(persistence, never()).store(any(), anyList());
    }
}
