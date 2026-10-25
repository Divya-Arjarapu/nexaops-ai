package com.nexaops.backend.rag;

public record RagErrorResponse(int status, String error, String message) {
}
