package com.nexaops.backend.admin;

import org.springframework.http.HttpStatus;

public class AdminAuthException extends RuntimeException {
    private final HttpStatus status;

    public AdminAuthException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus status() { return status; }
}
