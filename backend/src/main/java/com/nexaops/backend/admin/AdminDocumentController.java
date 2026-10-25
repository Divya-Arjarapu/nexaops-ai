package com.nexaops.backend.admin;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/documents")
public class AdminDocumentController {

    private final AdminDocumentService documentService;

    public AdminDocumentController(AdminDocumentService documentService) {
        this.documentService = documentService;
    }

    @GetMapping
    public List<ManagedDocument> list() {
        return documentService.list();
    }

    @DeleteMapping("/{documentId}")
    public ResponseEntity<Void> delete(@PathVariable String documentId) {
        UUID id;
        try {
            id = UUID.fromString(documentId);
        } catch (IllegalArgumentException exception) {
            throw new AdminDocumentException(HttpStatus.BAD_REQUEST, "A valid document ID is required.");
        }
        documentService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
