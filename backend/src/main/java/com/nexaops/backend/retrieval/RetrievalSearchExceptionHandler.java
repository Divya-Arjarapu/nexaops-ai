package com.nexaops.backend.retrieval;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RetrievalSearchController.class)
public class RetrievalSearchExceptionHandler {

    @ExceptionHandler(RetrievalException.class)
    public ResponseEntity<RetrievalErrorResponse> handleRetrievalError(RetrievalException exception) {
        return error(exception.status().value(), exception.status().getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RetrievalErrorResponse> handleUnexpectedError() {
        return error(500, "Internal Server Error", "The search request could not be completed.");
    }

    private static ResponseEntity<RetrievalErrorResponse> error(int status, String title, String message) {
        return ResponseEntity.status(status).body(new RetrievalErrorResponse(status, title, message));
    }
}
