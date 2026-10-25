package com.nexaops.backend.documents;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.support.MissingServletRequestPartException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

@RestControllerAdvice(assignableTypes = {DocumentUploadController.class, DocumentEmbeddingController.class})
public class DocumentUploadExceptionHandler {

    private static final Logger logger = LoggerFactory.getLogger(DocumentUploadExceptionHandler.class);

    @ExceptionHandler(DocumentUploadException.class)
    public ResponseEntity<DocumentUploadErrorResponse> handleDocumentUpload(DocumentUploadException exception) {
        return error(exception.status(), exception.getMessage());
    }

    @ExceptionHandler(MaxUploadSizeExceededException.class)
    public ResponseEntity<DocumentUploadErrorResponse> handleTooLarge() {
        return error(HttpStatus.PAYLOAD_TOO_LARGE, "The document exceeds the upload size limit.");
    }

    @ExceptionHandler(MissingServletRequestPartException.class)
    public ResponseEntity<DocumentUploadErrorResponse> handleMissingFile() {
        return error(HttpStatus.BAD_REQUEST, "A file must be supplied.");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<DocumentUploadErrorResponse> handleUnexpectedFailure(Exception exception) {
        Throwable causeException = exception.getCause();
        String cause = causeException == null
                ? "none"
                : causeException.getClass().getName()
                        + (causeException instanceof LinkageError ? ": " + causeException.getMessage() : "");
        logger.error("Unexpected document upload failure ({}; cause {})", exception.getClass().getName(), cause);
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "The document upload could not be completed.");
    }

    private static ResponseEntity<DocumentUploadErrorResponse> error(HttpStatus status, String message) {
        return ResponseEntity.status(status)
                .body(new DocumentUploadErrorResponse(status.value(), status.getReasonPhrase(), message));
    }
}
