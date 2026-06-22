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

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;

    /**
     * Parses the file, chunks it, appends metadata security tags, and saves it.
     */
    @Transactional
    public Document ingest(MultipartFile file, String title, String departmentOwner, String requiredRole, String username) throws IOException {

        // 1. Save Metastore details in standard relational database
        String mockFilePath = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        Document document = Document.builder()
                .title(title)
                .filePath(mockFilePath)
                .uploadedBy(username)
                .departmentOwner(departmentOwner)
                .requiredRole(requiredRole)
                .build();

        final Document savedDocument = documentRepository.save(document);

        // 2. Parse file content using Apache Tika (Spring AI wrapper)
        TikaDocumentReader tikaReader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<org.springframework.ai.document.Document> parsedDocuments = tikaReader.get();

        // 3. Chunk the document using the splitter builder to avoid deprecation warnings
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .build();
        List<org.springframework.ai.document.Document> chunks = splitter.apply(parsedDocuments);

        // 4. Populate security tags into each chunk's metadata context (Pre-Retrieval Hook)
        for (org.springframework.ai.document.Document chunk : chunks) {
            chunk.getMetadata().put("document_id", savedDocument.getId());
            chunk.getMetadata().put("department_owner", departmentOwner);
            chunk.getMetadata().put("required_role", requiredRole);
        }

        // 5. Generate embeddings and save to PostgreSQL + pgvector
        vectorStore.add(chunks);

        // 6. Save relational chunk mapping for querying/cascading
        for (int i = 0; i < chunks.size(); i++) {
            org.springframework.ai.document.Document chunk = chunks.get(i);
            DocumentChunk documentChunk = DocumentChunk.builder()
                    .document(savedDocument)
                    .chunkIndex(i)
                    .content(chunk.getText())
                    .embeddingId(chunk.getId())
                    .build();
            documentChunkRepository.save(documentChunk);
        }

        return savedDocument;
    }

    /**
     * Deletes a document, its associated relational chunks, and its vector embeddings.
     */
    @Transactional
    public void deleteDocument(Long documentId) {
        Document document = documentRepository.findById(documentId)
                .orElseThrow(() -> new IllegalArgumentException("Document not found with ID: " + documentId));

        // 1. Retrieve associated relational chunks
        List<DocumentChunk> chunks = documentChunkRepository.findByDocumentId(documentId);

        // 2. Extract vector store embedding IDs
        List<String> vectorIds = chunks.stream()
                .map(DocumentChunk::getEmbeddingId)
                .toList();

        // 3. Purge vector embeddings from pgvector store
        if (!vectorIds.isEmpty()) {
            vectorStore.delete(vectorIds);
        }

        // 4. Delete the document metadata record (triggers Cascade delete on relational document_chunks table)
        documentRepository.delete(document);
    }
}
