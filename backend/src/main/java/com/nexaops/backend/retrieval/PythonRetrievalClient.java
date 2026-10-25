package com.nexaops.backend.retrieval;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonRetrievalClient {

    private final RestClient restClient;

    public PythonRetrievalClient(@Qualifier("retrievalRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public RetrievalSearchResponse search(String query, int topK) {
        try {
            RetrievalSearchResponse response = restClient.post()
                    .uri("/retrieval/search")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PythonSearchRequest(query, topK))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, upstream) -> {
                                throw upstreamFailure(upstream.getStatusCode().value());
                            })
                    .body(RetrievalSearchResponse.class);
            if (!isValidResponse(response, query, topK)) {
                throw new RetrievalException(HttpStatus.BAD_GATEWAY, "The retrieval service returned an invalid response.");
            }
            return response;
        } catch (RetrievalException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new RetrievalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The retrieval service is unavailable or timed out.", exception);
        } catch (RestClientException exception) {
            throw new RetrievalException(HttpStatus.BAD_GATEWAY,
                    "The retrieval service returned an invalid response.", exception);
        }
    }

    private static boolean isValidResponse(RetrievalSearchResponse response, String query, int topK) {
        if (response == null || !query.equals(response.query()) || response.results() == null
                || response.resultCount() != response.results().size() || response.results().size() > topK) {
            return false;
        }
        return response.results().stream().allMatch(result -> result != null
                && result.documentId() != null && result.filename() != null && result.chunkId() != null
                && result.chunkIndex() >= 0 && result.text() != null
                && Double.isFinite(result.similarityScore()));
    }

    private static RetrievalException upstreamFailure(int statusCode) {
        if (statusCode == 413 || statusCode == 422) {
            return new RetrievalException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The query or result limit is outside the supported range.");
        }
        if (statusCode == 503) {
            return new RetrievalException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Semantic retrieval is unavailable. Check the Python service, Ollama, and PostgreSQL.");
        }
        return new RetrievalException(HttpStatus.BAD_GATEWAY,
                "The retrieval service could not complete the search.");
    }

    private record PythonSearchRequest(String query, @JsonProperty("top_k") int topK) {
    }
}
