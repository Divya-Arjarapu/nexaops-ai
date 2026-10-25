package com.nexaops.backend.documents;

public record DocumentUploadErrorResponse(int status, String error, String message) {
}
