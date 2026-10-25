package com.nexaops.backend.rag;

import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice(assignableTypes = RagAnswerController.class)
public class RagAnswerExceptionHandler {

    @ExceptionHandler(RagException.class)
    public ResponseEntity<RagErrorResponse> handleRagError(RagException exception) {
        return error(exception.status().value(), exception.status().getReasonPhrase(), exception.getMessage());
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<RagErrorResponse> handleInvalidRequest() {
        return error(400, "Bad Request", "The request body or its field types are invalid.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<RagErrorResponse> handleUnexpectedError() {
        return error(500, "Internal Server Error", "The answer request could not be completed.");
    }

    private static ResponseEntity<RagErrorResponse> error(int status, String title, String message) {
        return ResponseEntity.status(status).body(new RagErrorResponse(status, title, message));
    }
}
