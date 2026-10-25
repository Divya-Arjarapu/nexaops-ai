package com.nexaops.backend.rag;

import java.util.HashSet;
import java.util.Set;

import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Component
public class PythonRagClient {

    private final RestClient restClient;

    public PythonRagClient(@Qualifier("ragRestClient") RestClient restClient) {
        this.restClient = restClient;
    }

    public RagAnswerResponse answer(String query, int topK) {
        try {
            RagAnswerResponse response = restClient.post()
                    .uri("/rag/answer")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(new PythonRagRequest(query, topK))
                    .retrieve()
                    .onStatus(status -> status.is4xxClientError() || status.is5xxServerError(),
                            (request, upstream) -> {
                                throw upstreamFailure(upstream.getStatusCode().value());
                            })
                    .body(RagAnswerResponse.class);
            if (!isValidResponse(response, query, topK)) {
                throw new RagException(HttpStatus.BAD_GATEWAY, "The answer service returned an invalid response.");
            }
            return response;
        } catch (RagException exception) {
            throw exception;
        } catch (ResourceAccessException exception) {
            throw new RagException(HttpStatus.SERVICE_UNAVAILABLE,
                    "The answer service is unavailable or timed out.", exception);
        } catch (RestClientException exception) {
            throw new RagException(HttpStatus.BAD_GATEWAY,
                    "The answer service returned an invalid response.", exception);
        }
    }

    private static boolean isValidResponse(RagAnswerResponse response, String query, int topK) {
        if (response == null || !query.equals(response.query()) || response.answer() == null
                || response.answer().isBlank() || response.citations() == null || response.sources() == null
                || response.sources().size() > topK || (response.hasContext() && response.sources().isEmpty())) {
            return false;
        }
        Set<String> sourceIds = new HashSet<>();
        for (RagSource source : response.sources()) {
            if (source == null || source.sourceId() == null || !sourceIds.add(source.sourceId())
                    || source.documentId() == null || source.filename() == null || source.chunkId() == null
                    || source.chunkIndex() < 0 || source.text() == null || !Double.isFinite(source.similarityScore())) {
                return false;
            }
        }
        Set<String> citationIds = new HashSet<>();
        if (!response.hasContext() && !response.citations().isEmpty()) {
            return false;
        }
        for (RagCitation citation : response.citations()) {
            if (citation == null || citation.sourceId() == null || !citationIds.add(citation.sourceId())
                    || !sourceIds.contains(citation.sourceId()) || citation.documentId() == null
                    || citation.filename() == null || citation.chunkId() == null || citation.chunkIndex() < 0
                    || !Double.isFinite(citation.similarityScore())) {
                return false;
            }
        }
        return true;
    }

    private static RagException upstreamFailure(int statusCode) {
        if (statusCode == 413 || statusCode == 422) {
            return new RagException(HttpStatus.UNPROCESSABLE_ENTITY,
                    "The question or result limit is outside the supported range.");
        }
        if (statusCode == 503) {
            return new RagException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Answer generation is unavailable. Check the Python service and local Ollama model.");
        }
        return new RagException(HttpStatus.BAD_GATEWAY,
                "The answer service could not complete the request.");
    }

    private record PythonRagRequest(String query, @JsonProperty("top_k") int topK) {
    }
}
