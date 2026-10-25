package com.nexaops.backend.admin;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = {AdminAuthController.class, AdminDocumentController.class}, properties = {
        "nexaops.admin.username=workspace-admin", "nexaops.admin.password=local-test-secret"
})
@Import({AdminSecurityConfiguration.class, AdminAuthService.class})
class AdminControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private AdminAuthService authService;

    @MockitoBean
    private AdminDocumentService documentService;

    @Test
    void loginReturnsOpaqueTokenAndRejectsInvalidCredentials() throws Exception {
        MvcResult result = mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"workspace-admin\",\"password\":\"local-test-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.expiresAt").isNotEmpty())
                .andReturn();
        String token = com.jayway.jsonpath.JsonPath.read(result.getResponse().getContentAsString(), "$.token");
        org.junit.jupiter.api.Assertions.assertTrue(authService.isValid(token));

        mockMvc.perform(post("/api/admin/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"username\":\"workspace-admin\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("The username or password is incorrect."));
    }

    @Test
    void documentManagementAndUploadsRequireAuthenticatedAdmin() throws Exception {
        mockMvc.perform(get("/api/admin/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/admin/documents/{id}", UUID.randomUUID())).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/documents/{id}/embeddings", UUID.randomUUID())).andExpect(status().isUnauthorized());
    }

    @Test
    void authenticatedAdminCanListAndDeleteSelectedDocument() throws Exception {
        String token = authService.login("workspace-admin", "local-test-secret").token();
        UUID id = UUID.randomUUID();
        when(documentService.list()).thenReturn(List.of(new ManagedDocument(
                id, "policy.txt", OffsetDateTime.parse("2026-10-24T12:30:00Z"), "ready", 3, 2)));

        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].documentId").value(id.toString()))
                .andExpect(jsonPath("$[0].filename").value("policy.txt"))
                .andExpect(jsonPath("$[0].chunkCount").value(3))
                .andExpect(jsonPath("$[0].embeddedChunkCount").value(2));

        mockMvc.perform(delete("/api/admin/documents/{id}", id).header("Authorization", "Bearer " + token))
                .andExpect(status().isNoContent());
        verify(documentService).delete(id);
    }

    @Test
    void invalidDocumentIdentifierIsRejected() throws Exception {
        String token = authService.login("workspace-admin", "local-test-secret").token();
        mockMvc.perform(delete("/api/admin/documents/not-a-uuid").header("Authorization", "Bearer " + token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("A valid document ID is required."));
    }

    @Test
    void documentDatabaseFailureReturnsSafeUnavailableResponse() throws Exception {
        String token = authService.login("workspace-admin", "local-test-secret").token();
        when(documentService.list()).thenThrow(new AdminDocumentException(
                org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE, "The document list could not be loaded. Please try again."));
        mockMvc.perform(get("/api/admin/documents").header("Authorization", "Bearer " + token))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("The document list could not be loaded. Please try again."));
    }
}
