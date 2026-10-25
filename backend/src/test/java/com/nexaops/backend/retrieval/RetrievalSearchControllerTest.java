package com.nexaops.backend.retrieval;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RetrievalSearchController.class)
class RetrievalSearchControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RetrievalSearchService retrievalService;

    @Test
    void returnsRetrievedSourceExcerptsWithoutGeneratingAnAnswer() throws Exception {
        when(retrievalService.search(new RetrievalSearchRequest("Who approves production access?", 5)))
                .thenReturn(new RetrievalSearchResponse(
                        "Who approves production access?",
                        List.of(new RetrievalSearchResult(
                                "doc-1", "policy.pdf", "8", 0, 2, "Approval", "Page 2",
                                "A system administrator must approve access.", 0.84
                        )),
                        1
                ));

        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Who approves production access?\",\"top_k\":5}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.resultCount").value(1))
                .andExpect(jsonPath("$.results[0].filename").value("policy.pdf"))
                .andExpect(jsonPath("$.results[0].pageNumber").value(2))
                .andExpect(jsonPath("$.results[0].similarityScore").value(0.84))
                .andExpect(jsonPath("$.results[0].text").value("A system administrator must approve access."));
    }

    @Test
    void returnsSafeStructuredErrors() throws Exception {
        when(retrievalService.search(new RetrievalSearchRequest("query", 5)))
                .thenThrow(new RetrievalException(
                        org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "The retrieval service is unavailable or timed out."
                ));

        mockMvc.perform(post("/api/retrieval/search")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"query\",\"top_k\":5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The retrieval service is unavailable or timed out."));
    }
}
