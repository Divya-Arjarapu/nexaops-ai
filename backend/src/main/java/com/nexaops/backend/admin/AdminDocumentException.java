package com.nexaops.backend.admin;

import org.springframework.http.HttpStatus;

public class AdminDocumentException extends RuntimeException {
    private final HttpStatus status;

    public AdminDocumentException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
