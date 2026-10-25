package com.nexaops.backend.documents;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import com.nexaops.backend.admin.AdminAuthService;
import com.nexaops.backend.admin.AdminSecurityConfiguration;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentEmbeddingController.class, properties = {
        "nexaops.admin.username=test-admin", "nexaops.admin.password=test-secret"
})
@Import({AdminSecurityConfiguration.class, AdminAuthService.class})
class DocumentEmbeddingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuthService authService;

    @MockitoBean
    private DocumentEmbeddingService embeddingService;

    @Test
    void endpointReturnsStructuredEmbeddingProgress() throws Exception {
        UUID documentId = UUID.randomUUID();
        when(embeddingService.generate(documentId)).thenReturn(new DocumentEmbeddingResponse(
                documentId, "nomic-embed-text", 4, 3, 1, 0, "complete", "Embeddings are stored."
        ));

        String token = authService.login("test-admin", "test-secret").token();
        mockMvc.perform(post("/api/documents/{documentId}/embeddings", documentId)
                        .header("Authorization", "Bearer " + token)
                        .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.documentId").value(documentId.toString()))
                .andExpect(jsonPath("$.embeddingModel").value("nomic-embed-text"))
                .andExpect(jsonPath("$.totalChunkCount").value(4))
                .andExpect(jsonPath("$.newlyEmbeddedChunkCount").value(3))
                .andExpect(jsonPath("$.alreadyEmbeddedChunkCount").value(1))
                .andExpect(jsonPath("$.failedChunkCount").value(0));
    }
}
