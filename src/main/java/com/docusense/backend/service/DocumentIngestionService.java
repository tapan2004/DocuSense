package com.docusense.backend.service;

import com.docusense.backend.model.Document;
import com.docusense.backend.model.DocumentChunk;
import com.docusense.backend.repository.DocumentRepository;
import com.docusense.backend.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;

    @Transactional
    public Document ingest(MultipartFile file, String title, String departmentOwner, String requiredRole, String username) throws IOException {

        // 1. Save Metastore details in relational database
        String mockFilePath = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        Document document = Document.builder()
                .title(title)
                .filePath(mockFilePath)
                .uploadedBy(username)
                .departmentOwner(departmentOwner)
                .requiredRole(requiredRole)
                .build();

        final Document savedDocument = documentRepository.save(document);

        // 2. Parse file content using Apache Tika
        TikaDocumentReader tikaReader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<org.springframework.ai.document.Document> parsedDocuments = tikaReader.get();

        // 3. Split into Parent Chunks (1200 tokens, 200 overlap)
        TokenTextSplitter parentSplitter = TokenTextSplitter.builder()
                .withChunkSize(1200)
                .build();
        List<org.springframework.ai.document.Document> parentChunks = parentSplitter.apply(parsedDocuments);

        // 4. Subdivide into Child Chunks (300 tokens, 50 overlap)
        TokenTextSplitter childSplitter = TokenTextSplitter.builder()
                .withChunkSize(300)
                .build();

        List<org.springframework.ai.document.Document> vectorStoreChunks = new ArrayList<>();
        List<DocumentChunk> dbChunksToSave = new ArrayList<>();

        int childIndex = 0;
        for (org.springframework.ai.document.Document parent : parentChunks) {
            String parentText = parent.getText();
            List<org.springframework.ai.document.Document> children = childSplitter.apply(List.of(parent));

            for (org.springframework.ai.document.Document child : children) {
                // Generate a custom ID for the child chunk vector
                String childId = UUID.randomUUID().toString();

                org.springframework.ai.document.Document vectorChunk = new org.springframework.ai.document.Document(
                        childId,
                        child.getText(),
                        new HashMap<>(child.getMetadata())
                );

                // Populate security tags
                vectorChunk.getMetadata().put("document_id", savedDocument.getId());
                vectorChunk.getMetadata().put("department_owner", departmentOwner);
                vectorChunk.getMetadata().put("required_role", requiredRole);

                vectorStoreChunks.add(vectorChunk);

                // Create database relational mapping linking child to parent text
                DocumentChunk dbChunk = DocumentChunk.builder()
                        .document(savedDocument)
                        .chunkIndex(childIndex++)
                        .content(child.getText())
                        .parentContent(parentText) // Store the larger context
                        .embeddingId(childId)
                        .build();
                dbChunksToSave.add(dbChunk);
            }
        }

        // 5. Ingest child vectors in pgvector
        vectorStore.add(vectorStoreChunks);

        // 6. Persist text blocks and mapping IDs in SQL
        for (DocumentChunk dbChunk : dbChunksToSave) {
            documentChunkRepository.save(dbChunk);
        }

        return savedDocument;
    }

    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(documentId);
        List<String> vectorIds = chunks.stream()
                .map(DocumentChunk::getEmbeddingId)
                .toList();

        if (!vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }

        documentRepository.delete(document);
    }
}
