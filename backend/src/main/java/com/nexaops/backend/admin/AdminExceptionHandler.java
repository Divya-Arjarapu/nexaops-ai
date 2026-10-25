package com.nexaops.backend.admin;

import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = {AdminAuthController.class, AdminDocumentController.class})
public class AdminExceptionHandler {

    @ExceptionHandler(AdminAuthException.class)
    public ResponseEntity<AdminErrorResponse> handleAuth(AdminAuthException exception) {
        return ResponseEntity.status(exception.status()).body(new AdminErrorResponse(
                exception.status().value(), exception.status().getReasonPhrase(), exception.getMessage()));
    }

    @ExceptionHandler(AdminDocumentException.class)
    public ResponseEntity<AdminErrorResponse> handleDocuments(AdminDocumentException exception) {
        return ResponseEntity.status(exception.status()).body(new AdminErrorResponse(
                exception.status().value(), exception.status().getReasonPhrase(), exception.getMessage()));
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<AdminErrorResponse> handleDatabaseFailure() {
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(new AdminErrorResponse(
                503, "Service Unavailable", "The document request could not be completed. Please try again."));
    }
}
