package com.nexaops.backend.rag;

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

@WebMvcTest(RagAnswerController.class)
class RagAnswerControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RagAnswerService ragAnswerService;

    @Test
    void returnsAnswerCitationsAndRetrievedSources() throws Exception {
        RagCitation citation = new RagCitation("S1", "doc-1", "policy.pdf", "9", 2, 4,
                "Approval", "Page 4", 0.88);
        RagSource source = new RagSource("S1", "doc-1", "policy.pdf", "9", 2, 4,
                "Approval", "Page 4", 0.88, "Administrator approval is required.");
        when(ragAnswerService.answer(new RagAnswerRequest("Who approves access?", 5)))
                .thenReturn(new RagAnswerResponse("Who approves access?", "A system administrator [S1].",
                        true, List.of(citation), List.of(source)));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"Who approves access?\",\"top_k\":5}"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.hasContext").value(true))
                .andExpect(jsonPath("$.answer").value("A system administrator [S1]."))
                .andExpect(jsonPath("$.citations[0].filename").value("policy.pdf"))
                .andExpect(jsonPath("$.citations[0].pageNumber").value(4))
                .andExpect(jsonPath("$.sources[0].text").value("Administrator approval is required."));
    }

    @Test
    void returnsSafeStructuredErrorsForPythonFailures() throws Exception {
        when(ragAnswerService.answer(new RagAnswerRequest("query", 5)))
                .thenThrow(new RagException(org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE,
                        "The answer service is unavailable or timed out."));

        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"query\",\"top_k\":5}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The answer service is unavailable or timed out."));
    }

    @Test
    void rejectsMalformedRequestFieldTypesWithSafeBadRequest() throws Exception {
        mockMvc.perform(post("/api/rag/answer")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"query\":\"query\",\"top_k\":\"many\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request body or its field types are invalid."));
    }
}
