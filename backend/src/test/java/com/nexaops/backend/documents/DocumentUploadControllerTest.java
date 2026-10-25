package com.nexaops.backend.documents;

import java.util.UUID;

import com.nexaops.backend.admin.AdminAuthService;
import com.nexaops.backend.admin.AdminSecurityConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = DocumentUploadController.class, properties = {
        "nexaops.admin.username=test-admin", "nexaops.admin.password=test-secret"
})
@Import({AdminSecurityConfiguration.class, AdminAuthService.class})
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuthService authService;

    @MockitoBean
    private DocumentUploadService uploadService;

    @Test
    void rejectsAnonymousUploadAndAllowsRepeatedAuthenticatedRequests() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "same.txt", "text/plain", "content".getBytes());
        UUID firstId = UUID.randomUUID();
        UUID secondId = UUID.randomUUID();
        when(uploadService.upload(any())).thenReturn(
                new DocumentUploadResponse(firstId, "same.txt", "ready", 7, null, 1, 0, "stored"),
                new DocumentUploadResponse(secondId, "same.txt", "ready", 7, null, 1, 0, "stored"));

        mockMvc.perform(multipart("/api/documents").file(file)).andExpect(status().isUnauthorized());
        String token = authService.login("test-admin", "test-secret").token();
        mockMvc.perform(multipart("/api/documents").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.documentId").value(firstId.toString()));
        mockMvc.perform(multipart("/api/documents").file(file).header("Authorization", "Bearer " + token))
                .andExpect(status().isCreated()).andExpect(jsonPath("$.documentId").value(secondId.toString()));
    }
}
