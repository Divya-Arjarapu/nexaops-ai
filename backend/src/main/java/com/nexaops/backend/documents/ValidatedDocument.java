package com.nexaops.backend.documents;

public record ValidatedDocument(
        String filename,
        String mediaType,
        byte[] content,
        String sha256
) {
}
