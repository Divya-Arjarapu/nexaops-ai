package com.nexaops.backend.documents;

import org.springframework.core.io.ByteArrayResource;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DocumentExtractionClient {

    private final RestClient restClient;

    public DocumentExtractionClient(
            @Qualifier("documentExtractionRestClient") RestClient documentExtractionRestClient
    ) {
        this.restClient = documentExtractionRestClient;
    }

    public DocumentExtractionResponse extract(ValidatedDocument document) {
        ByteArrayResource resource = new ByteArrayResource(document.content()) {
            @Override
            public String getFilename() {
                return document.filename();
            }
        };
        MultipartBodyBuilder parts = new MultipartBodyBuilder();
        parts.part("file", resource)
                .filename(document.filename())
                .contentType(MediaType.parseMediaType(document.mediaType()));

        try {
            DocumentExtractionResponse response = restClient.post()
                    .uri("/documents/extract")
                    .contentType(MediaType.MULTIPART_FORM_DATA)
                    .body(parts.build())
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, upstream) -> {
                                throw upstreamFailure(upstream.getStatusCode().value());
                            })
                    .body(DocumentExtractionResponse.class);
            if (response == null || response.pages() == null) {
                throw new DocumentUploadException(
                        HttpStatus.BAD_GATEWAY,
                        "The extraction service returned an invalid response."
                );
            }
            return response;
        } catch (DocumentUploadException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new DocumentUploadException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The document extraction service is unavailable or timed out.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new DocumentUploadException(
                    HttpStatus.BAD_GATEWAY,
                    "The document extraction service returned an invalid response.",
                    exception
            );
        }
    }

    private static DocumentUploadException upstreamFailure(int statusCode) {
        return switch (statusCode) {
            case 400 -> new DocumentUploadException(HttpStatus.BAD_REQUEST, "The extraction service could not read the uploaded document.");
            case 413 -> new DocumentUploadException(HttpStatus.PAYLOAD_TOO_LARGE, "The document exceeds the extraction service limits.");
            case 415 -> new DocumentUploadException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "The extraction service does not support this file format.");
            case 422 -> new DocumentUploadException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "The document could not be extracted. Encrypted, invalid, and image-only PDFs are not supported."
            );
            default -> new DocumentUploadException(
                    HttpStatus.BAD_GATEWAY,
                    "The document extraction service returned an error."
            );
        };
    }
}
