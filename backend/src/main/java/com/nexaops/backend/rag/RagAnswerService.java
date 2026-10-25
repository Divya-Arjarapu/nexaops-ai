package com.nexaops.backend.rag;

import org.springframework.http.HttpStatus;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class RagAnswerService {

    private final PythonRagClient ragClient;
    private final int maxQueryLength;
    private final int defaultTopK;
    private final int maxTopK;

    public RagAnswerService(
            PythonRagClient ragClient,
            @Value("${nexaops.retrieval.max-query-length:2000}") int maxQueryLength,
            @Value("${nexaops.retrieval.default-top-k:5}") int defaultTopK,
            @Value("${nexaops.retrieval.max-top-k:10}") int maxTopK
    ) {
        if (maxQueryLength < 1 || defaultTopK < 1 || maxTopK < defaultTopK || maxTopK > 10) {
            throw new IllegalArgumentException("RAG query and top_k settings are invalid.");
        }
        this.ragClient = ragClient;
        this.maxQueryLength = maxQueryLength;
        this.defaultTopK = defaultTopK;
        this.maxTopK = maxTopK;
    }

    public RagAnswerResponse answer(RagAnswerRequest request) {
        String query = request == null || request.query() == null ? "" : request.query().strip();
        if (query.isEmpty()) {
            throw new RagException(HttpStatus.BAD_REQUEST, "A non-empty question is required.");
        }
        if (query.length() > maxQueryLength) {
            throw new RagException(HttpStatus.BAD_REQUEST, "The question exceeds the supported length.");
        }
        int topK = request.topK() == null ? defaultTopK : request.topK();
        if (topK < 1 || topK > maxTopK) {
            throw new RagException(HttpStatus.BAD_REQUEST, "top_k must be between 1 and " + maxTopK + ".");
        }
        return ragClient.answer(query, topK);
    }
}
