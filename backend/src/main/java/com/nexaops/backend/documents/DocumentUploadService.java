package com.nexaops.backend.documents;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentUploadService {

    private final DocumentUploadValidator validator;
    private final DocumentExtractionClient extractionClient;
    private final DocumentChunker chunker;
    private final DocumentPersistenceService persistenceService;

    public DocumentUploadService(
            DocumentUploadValidator validator,
            DocumentExtractionClient extractionClient,
            DocumentChunker chunker,
            DocumentPersistenceService persistenceService
    ) {
        this.validator = validator;
        this.extractionClient = extractionClient;
        this.chunker = chunker;
        this.persistenceService = persistenceService;
    }

    public DocumentUploadResponse upload(MultipartFile file) {
        ValidatedDocument document = validator.validate(file);
        DocumentExtractionResponse extraction = extractionClient.extract(document);
        List<DocumentChunk> chunks = chunker.chunk(extraction.pages());
        if (chunks.isEmpty()) {
            throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "The document contains no extractable text.");
        }

        UUID documentId = persistenceService.store(document, chunks);
        int characterCount = extraction.pages().stream()
                .map(ExtractedPage::text)
                .filter(text -> text != null)
                .mapToInt(String::length)
                .sum();
        return new DocumentUploadResponse(
                documentId,
                document.filename(),
                "ready",
                characterCount,
                extraction.pageCount(),
                chunks.size(),
                0,
                "Text extracted and stored; embeddings are pending."
        );
    }
}
