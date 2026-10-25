package com.nexaops.backend.retrieval;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withException;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class PythonRetrievalClientTest {

    @Test
    void postsSnakeCaseRequestAndMapsSourceMetadata() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://python.test/retrieval/search"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query":"Who approves access?","top_k":3}
                        """))
                .andRespond(withSuccess("""
                        {"query":"Who approves access?","result_count":1,"results":[
                          {"document_id":"doc-1","filename":"policy.pdf","chunk_id":"25",
                           "chunk_index":2,"page_number":4,"section_title":"Approval",
                           "source_locator":"Page 4","text":"Administrator approval is required.",
                           "similarity_score":0.88}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        RetrievalSearchResponse response = new PythonRetrievalClient(builder.build())
                .search("Who approves access?", 3);

        assertEquals(1, response.resultCount());
        assertEquals("doc-1", response.results().getFirst().documentId());
        assertEquals("policy.pdf", response.results().getFirst().filename());
        assertEquals(4, response.results().getFirst().pageNumber());
        assertEquals(0.88, response.results().getFirst().similarityScore());
        server.verify();
    }

    @Test
    void mapsPythonOutageTimeoutAndMalformedResponseToSafeErrors() {
        RestClient.Builder unavailableBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer unavailableServer = MockRestServiceServer.bindTo(unavailableBuilder).build();
        unavailableServer.expect(requestTo("http://python.test/retrieval/search"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        RetrievalException unavailable = assertThrows(RetrievalException.class,
                () -> new PythonRetrievalClient(unavailableBuilder.build()).search("query", 5));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.status());
        assertEquals("Semantic retrieval is unavailable. Check the Python service, Ollama, and PostgreSQL.", unavailable.getMessage());

        RestClient.Builder timeoutBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer timeoutServer = MockRestServiceServer.bindTo(timeoutBuilder).build();
        timeoutServer.expect(requestTo("http://python.test/retrieval/search"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(RetrievalException.class,
                () -> new PythonRetrievalClient(timeoutBuilder.build()).search("query", 5)).status());

        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer malformedServer = MockRestServiceServer.bindTo(malformedBuilder).build();
        malformedServer.expect(requestTo("http://python.test/retrieval/search"))
                .andRespond(withSuccess("{\"query\":\"query\",\"result_count\":2,\"results\":[]}", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(RetrievalException.class,
                () -> new PythonRetrievalClient(malformedBuilder.build()).search("query", 5)).status());
    }
}
