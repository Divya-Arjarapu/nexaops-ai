package com.nexaops.backend.documents;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DocumentUploadValidatorTest {

    private final DocumentUploadValidator validator = new DocumentUploadValidator(16);

    @Test
    void acceptsTextAndUsesOnlyTheSafeFilename() {
        ValidatedDocument document = validator.validate(new MockMultipartFile(
                "file", "../../notes.txt", "text/plain; charset=UTF-8", "hello".getBytes()
        ));

        assertEquals("notes.txt", document.filename());
        assertEquals("text/plain", document.mediaType());
        assertEquals(64, document.sha256().length());
    }

    @Test
    void rejectsEmptyUnsupportedMismatchedAndOversizedFiles() {
        assertEquals(HttpStatus.BAD_REQUEST, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("empty.txt", "text/plain", new byte[0]))).status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("brief.docx", "application/octet-stream", "hello".getBytes()))).status());
        assertEquals(HttpStatus.UNSUPPORTED_MEDIA_TYPE, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("brief.txt", "application/pdf", "hello".getBytes()))).status());
        assertEquals(HttpStatus.PAYLOAD_TOO_LARGE, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("large.txt", "text/plain", new byte[17]))).status());
    }

    @Test
    void verifiesPdfSignatureAndUtf8Text() {
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("fake.pdf", "application/pdf", "not pdf".getBytes()))).status());
        assertEquals(HttpStatus.UNPROCESSABLE_ENTITY, assertThrows(DocumentUploadException.class,
                () -> validator.validate(file("binary.txt", "text/plain", new byte[]{(byte) 0xff}))).status());
    }

    private static MockMultipartFile file(String name, String contentType, byte[] bytes) {
        return new MockMultipartFile("file", name, contentType, bytes);
    }
}
