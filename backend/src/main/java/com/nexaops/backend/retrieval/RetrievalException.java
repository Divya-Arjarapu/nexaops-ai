package com.nexaops.backend.retrieval;

import org.springframework.http.HttpStatus;

public class RetrievalException extends RuntimeException {

    private final HttpStatus status;

    public RetrievalException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public RetrievalException(HttpStatus status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public HttpStatus status() {
        return status;
    }
}
