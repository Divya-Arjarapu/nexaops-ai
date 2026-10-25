package com.nexaops.backend.rag;

import java.net.SocketTimeoutException;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
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

class PythonRagClientTest {

    @Test
    void postsPythonContractAndMapsAnswerCitationsAndSources() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        server.expect(requestTo("http://python.test/rag/answer"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().json("""
                        {"query":"Who approves access?","top_k":3}
                        """))
                .andRespond(withSuccess("""
                        {"query":"Who approves access?","answer":"An administrator approves access [S1].",
                         "has_context":true,"citations":[
                          {"source_id":"S1","document_id":"doc-1","filename":"policy.pdf",
                           "chunk_id":"9","chunk_index":2,"page_number":4,"section_title":"Approval",
                           "source_locator":"Page 4","similarity_score":0.88}],
                         "sources":[
                          {"source_id":"S1","document_id":"doc-1","filename":"policy.pdf",
                           "chunk_id":"9","chunk_index":2,"page_number":4,"section_title":"Approval",
                           "source_locator":"Page 4","similarity_score":0.88,
                           "text":"Administrator approval is required."}]}
                        """, MediaType.APPLICATION_JSON));

        RagAnswerResponse response = new PythonRagClient(builder.build()).answer("Who approves access?", 3);

        assertEquals("An administrator approves access [S1].", response.answer());
        assertEquals(1, response.citations().size());
        assertEquals("S1", response.citations().getFirst().sourceId());
        assertEquals("policy.pdf", response.sources().getFirst().filename());
        assertEquals(4, response.sources().getFirst().pageNumber());
        assertEquals("Administrator approval is required.", response.sources().getFirst().text());
        server.verify();
    }

    @Test
    void acceptsNoContextAndRejectsInvalidCitationOrMalformedResponse() {
        RestClient.Builder emptyBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer emptyServer = MockRestServiceServer.bindTo(emptyBuilder).build();
        emptyServer.expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withSuccess("""
                        {"query":"query","answer":"No passages were found.","has_context":false,
                         "citations":[],"sources":[]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(false, new PythonRagClient(emptyBuilder.build()).answer("query", 5).hasContext());

        RestClient.Builder unsupportedBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer.bindTo(unsupportedBuilder).build()
                .expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withSuccess("""
                        {"query":"query","answer":"The passage does not support this question.",
                         "has_context":false,"citations":[],"sources":[
                          {"source_id":"S1","document_id":"doc","filename":"f",
                           "chunk_id":"1","chunk_index":0,"similarity_score":0.5,"text":"source"}]}
                        """, MediaType.APPLICATION_JSON));
        RagAnswerResponse unsupported = new PythonRagClient(unsupportedBuilder.build()).answer("query", 5);
        assertEquals(false, unsupported.hasContext());
        assertEquals(1, unsupported.sources().size());

        RestClient.Builder invalidBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer.bindTo(invalidBuilder).build()
                .expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withSuccess("""
                        {"query":"query","answer":"Answer","has_context":true,
                         "citations":[{"source_id":"S99","document_id":"doc","filename":"f",
                           "chunk_id":"1","chunk_index":0,"similarity_score":0.5}],
                         "sources":[{"source_id":"S1","document_id":"doc","filename":"f",
                           "chunk_id":"1","chunk_index":0,"similarity_score":0.5,"text":"source"}]}
                        """, MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(RagException.class,
                () -> new PythonRagClient(invalidBuilder.build()).answer("query", 5)).status());

        RestClient.Builder malformedBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer.bindTo(malformedBuilder).build()
                .expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withSuccess("not-json", MediaType.APPLICATION_JSON));
        assertEquals(HttpStatus.BAD_GATEWAY, assertThrows(RagException.class,
                () -> new PythonRagClient(malformedBuilder.build()).answer("query", 5)).status());
    }

    @Test
    void mapsPythonFailuresAndTimeoutsToSafeErrors() {
        RestClient.Builder unavailableBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer.bindTo(unavailableBuilder).build()
                .expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withStatus(HttpStatus.SERVICE_UNAVAILABLE));
        RagException unavailable = assertThrows(RagException.class,
                () -> new PythonRagClient(unavailableBuilder.build()).answer("query", 5));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, unavailable.status());
        assertEquals("Answer generation is unavailable. Check the Python service and local Ollama model.",
                unavailable.getMessage());

        RestClient.Builder timeoutBuilder = RestClient.builder().baseUrl("http://python.test");
        MockRestServiceServer.bindTo(timeoutBuilder).build()
                .expect(requestTo("http://python.test/rag/answer"))
                .andRespond(withException(new SocketTimeoutException("timeout")));
        assertEquals(HttpStatus.SERVICE_UNAVAILABLE, assertThrows(RagException.class,
                () -> new PythonRagClient(timeoutBuilder.build()).answer("query", 5)).status());
    }
}
