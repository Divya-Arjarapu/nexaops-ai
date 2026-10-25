package com.nexaops.backend.documents;

import java.util.List;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class DocumentEmbeddingClient {

    private final RestClient restClient;

    public DocumentEmbeddingClient(
            @Qualifier("documentEmbeddingRestClient") RestClient documentEmbeddingRestClient
    ) {
        this.restClient = documentEmbeddingRestClient;
    }

    public EmbeddingGenerationResponse generate(String model, List<String> texts) {
        try {
            EmbeddingGenerationResponse response = restClient.post()
                    .uri("/embeddings/generate")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new EmbeddingGenerationRequest(texts))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, upstream) -> {
                                throw upstreamFailure(upstream.getStatusCode().value(), model);
                            })
                    .body(EmbeddingGenerationResponse.class);
            if (response == null || response.model() == null || response.embeddings() == null) {
                throw new DocumentUploadException(
                        HttpStatus.BAD_GATEWAY,
                        "The embedding service returned an invalid response."
                );
            }
            return response;
        } catch (DocumentUploadException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new DocumentUploadException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "The embedding service is unavailable or timed out.",
                    exception
            );
        } catch (RestClientException exception) {
            throw new DocumentUploadException(
                    HttpStatus.BAD_GATEWAY,
                    "The embedding service returned an invalid response.",
                    exception
            );
        }
    }

    private static DocumentUploadException upstreamFailure(int statusCode, String model) {
        if (statusCode == 503) {
            return new DocumentUploadException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    "Ollama is unavailable or the embedding model is missing. Check Ollama and install the model manually with `ollama pull " + model + "`."
            );
        }
        if (statusCode == 413) {
            return new DocumentUploadException(
                    HttpStatus.PAYLOAD_TOO_LARGE,
                    "The embedding batch exceeds the Python service limits."
            );
        }
        return new DocumentUploadException(
                HttpStatus.BAD_GATEWAY,
                "The embedding service could not generate valid vectors."
        );
    }
}
