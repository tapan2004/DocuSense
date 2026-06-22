package com.docusense.backend.controller;

import com.docusense.backend.dto.UploadResponse;
import com.docusense.backend.model.Document;
import com.docusense.backend.service.DocumentIngestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import java.util.Objects;

@RestController
@RequestMapping("/api/documents")
@RequiredArgsConstructor
public class DocumentController {

    private final DocumentIngestionService documentIngestionService;

    @PostMapping("/upload")
    public ResponseEntity<UploadResponse> uploadDocument(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam("departmentOwner") String departmentOwner,
            @RequestParam("requiredRole") String requiredRole) {

        try {
            // Get logged-in user's username
            String currentUsername = Objects.requireNonNull(SecurityContextHolder.getContext().getAuthentication()).getName();
            Document document = documentIngestionService.ingest(file, title, departmentOwner, requiredRole, currentUsername);
            return ResponseEntity.ok(new UploadResponse(document.getId(), document.getTitle(), "Document successfully processed and vector embeddings generated."));
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body(new UploadResponse(null, title, "Failed to process document: " + e.getMessage()));
        }
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
    public ResponseEntity<String> deleteDocument(@PathVariable("id") Long id) {
        try {
            documentIngestionService.deleteDocument(id);
            return ResponseEntity.ok("Document with ID " + id + " successfully deleted from database and vector store.");
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        } catch (Exception e) {
            return ResponseEntity.internalServerError().body("Failed to delete document: " + e.getMessage());
        }
    }
}
