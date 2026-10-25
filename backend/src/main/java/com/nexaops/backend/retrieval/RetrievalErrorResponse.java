package com.nexaops.backend.retrieval;

public record RetrievalErrorResponse(int status, String error, String message) {
}
