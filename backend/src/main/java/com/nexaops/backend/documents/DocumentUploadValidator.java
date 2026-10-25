package com.nexaops.backend.documents;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class DocumentUploadValidator {

    private final long maxUploadBytes;

    public DocumentUploadValidator(
            @Value("${nexaops.documents.max-upload-bytes}") long maxUploadBytes
    ) {
        if (maxUploadBytes < 1) {
            throw new IllegalArgumentException("Document upload limit must be positive.");
        }
        this.maxUploadBytes = maxUploadBytes;
    }

    public ValidatedDocument validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new DocumentUploadException(HttpStatus.BAD_REQUEST, "The uploaded document is empty.");
        }
        if (file.getSize() > maxUploadBytes) {
            throw new DocumentUploadException(HttpStatus.PAYLOAD_TOO_LARGE, "The document exceeds the upload size limit.");
        }

        String filename = safeFilename(file.getOriginalFilename());
        String extension = extensionOf(filename);
        String contentType = baseContentType(file.getContentType());
        byte[] content;
        try {
            content = file.getBytes();
        } catch (IOException exception) {
            throw new DocumentUploadException(HttpStatus.BAD_REQUEST, "The uploaded document could not be read.", exception);
        }
        if (content.length == 0) {
            throw new DocumentUploadException(HttpStatus.BAD_REQUEST, "The uploaded document is empty.");
        }
        if (content.length > maxUploadBytes) {
            throw new DocumentUploadException(HttpStatus.PAYLOAD_TOO_LARGE, "The document exceeds the upload size limit.");
        }

        String mediaType = switch (extension) {
            case "pdf" -> validatePdf(content, contentType);
            case "txt", "text" -> validateText(content, contentType);
            default -> throw new DocumentUploadException(
                    HttpStatus.UNSUPPORTED_MEDIA_TYPE,
                    "Only PDF and plain-text documents are supported."
            );
        };
        return new ValidatedDocument(filename, mediaType, content, sha256(content));
    }

    private static String safeFilename(String originalFilename) {
        String filename = originalFilename == null
                ? ""
                : originalFilename.replace('\\', '/').substring(
                        originalFilename.replace('\\', '/').lastIndexOf('/') + 1
                ).strip();
        if (filename.isEmpty() || filename.equals(".") || filename.equals("..")) {
            throw new DocumentUploadException(HttpStatus.BAD_REQUEST, "A valid filename is required.");
        }
        if (filename.length() > 255 || filename.chars().anyMatch(character -> character < 32 || character == 127)) {
            throw new DocumentUploadException(HttpStatus.BAD_REQUEST, "The filename is invalid or too long.");
        }
        return filename;
    }

    private static String extensionOf(String filename) {
        int period = filename.lastIndexOf('.');
        return period < 0 ? "" : filename.substring(period + 1).toLowerCase(Locale.ROOT);
    }

    private static String baseContentType(String contentType) {
        return contentType == null
                ? ""
                : contentType.split(";", 2)[0].strip().toLowerCase(Locale.ROOT);
    }

    private static String validatePdf(byte[] content, String contentType) {
        if (!contentType.equals("application/pdf") && !contentType.equals("application/octet-stream")) {
            throw new DocumentUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "PDF files must use application/pdf content type.");
        }
        int headerLength = Math.min(content.length, 1024);
        byte[] signature = "%PDF-".getBytes(StandardCharsets.US_ASCII);
        for (int offset = 0; offset <= headerLength - signature.length; offset++) {
            boolean matches = true;
            for (int index = 0; index < signature.length; index++) {
                if (content[offset + index] != signature[index]) {
                    matches = false;
                    break;
                }
            }
            if (matches) return "application/pdf";
        }
        throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "The uploaded file is not a valid PDF document.");
    }

    private static String validateText(byte[] content, String contentType) {
        if (!contentType.equals("text/plain") && !contentType.equals("application/octet-stream")) {
            throw new DocumentUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Text files must use text/plain content type.");
        }
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(content))
                    .toString();
            boolean hasBinaryControl = text.codePoints().anyMatch(codePoint ->
                    codePoint == 0 || (codePoint < 32 && codePoint != '\n' && codePoint != '\r' && codePoint != '\t' && codePoint != '\f')
                            || codePoint == 127
            );
            if (hasBinaryControl) {
                throw new DocumentUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The uploaded file is not plain text.");
            }
        } catch (CharacterCodingException exception) {
            throw new DocumentUploadException(HttpStatus.UNPROCESSABLE_ENTITY, "Text documents must use UTF-8 encoding.", exception);
        }
        return "text/plain";
    }

    private static String sha256(byte[] content) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }
}
