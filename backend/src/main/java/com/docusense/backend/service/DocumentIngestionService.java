package com.docusense.backend.service;

import com.docusense.backend.model.Document;
import com.docusense.backend.model.DocumentChunk;
import com.docusense.backend.repository.DocumentRepository;
import com.docusense.backend.repository.DocumentChunkRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.UUID;
import java.util.Collections;
import java.util.Comparator;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import org.springframework.retry.support.RetryTemplate;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final DocumentChunkRepository documentChunkRepository;
    private final VectorStore vectorStore;
    private final PiiRedactorService piiRedactorService;
    private final ChatClient chatClient;
    private final PlatformTransactionManager transactionManager;

    public Document ingest(MultipartFile file, String title, String departmentOwner, String requiredRole, String username) throws IOException {

        // 1. Prepare Metastore details
        String mockFilePath = "uploads/" + System.currentTimeMillis() + "_" + file.getOriginalFilename();

        Document document = Document.builder()
                .title(title)
                .filePath(mockFilePath)
                .uploadedBy(username)
                .departmentOwner(departmentOwner)
                .requiredRole(requiredRole)
                .build();

        // Save metadata document in a short transactional block
        TransactionTemplate transactionTemplate = new TransactionTemplate(transactionManager);
        final Document savedDocument = transactionTemplate.execute(status -> documentRepository.save(document));

        // 2. Parse file content using Apache Tika (outside transaction)
        TikaDocumentReader tikaReader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<org.springframework.ai.document.Document> parsedDocuments = tikaReader.get();

        // Redact PII in parsed text before chunking (outside transaction)
        List<org.springframework.ai.document.Document> redactedDocuments = parsedDocuments.stream()
                .map(doc -> new org.springframework.ai.document.Document(
                        doc.getId(),
                        piiRedactorService.redact(doc.getText()),
                        doc.getMetadata()
                ))
                .toList();

        // 3. Split into Parent Chunks (1200 tokens)
        TokenTextSplitter parentSplitter = TokenTextSplitter.builder()
                .withChunkSize(1200)
                .build();
        List<org.springframework.ai.document.Document> parentChunks = parentSplitter.apply(redactedDocuments);

        // 4. Subdivide into Child Chunks (300 tokens)
        TokenTextSplitter childSplitter = TokenTextSplitter.builder()
                .withChunkSize(300)
                .build();

        // Retry template with exponential backoff for API robustness
        RetryTemplate retryTemplate = RetryTemplate.builder()
                .maxAttempts(3)
                .exponentialBackoff(1000, 2.0, 10000)
                .retryOn(Exception.class)
                .build();

        List<org.springframework.ai.document.Document> vectorStoreChunks = Collections.synchronizedList(new ArrayList<>());
        List<DocumentChunk> dbChunksToSave = Collections.synchronizedList(new ArrayList<>());

        ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();
        List<Future<Void>> futures = new ArrayList<>();

        int childIndex = 0;
        for (org.springframework.ai.document.Document parent : parentChunks) {
            String parentText = parent.getText();
            List<org.springframework.ai.document.Document> children = childSplitter.apply(List.of(parent));

            for (org.springframework.ai.document.Document child : children) {
                final int index = childIndex++;
                futures.add(executor.submit(() -> {
                    String childId = UUID.randomUUID().toString();
                    String contextualizedContent = child.getText();
                    try {
                        String contextualPrompt = String.format("""
                                Given this section of a document and a specific subsection chunk, write a 1-sentence contextual prefix that situates the subsection within the wider section. Keep it under 25 words. Do not write introductory text.
                                
                                SECTION:
                                %s
                                
                                SUBSECTION:
                                %s
                                """, parentText, child.getText());
                        
                        String contextPrefix = retryTemplate.execute(context -> 
                            chatClient.prompt()
                                    .user(contextualPrompt)
                                    .call()
                                    .content()
                        );
                        
                        if (contextPrefix != null && !contextPrefix.isBlank()) {
                            contextualizedContent = contextPrefix.trim() + "\n" + child.getText();
                            System.out.println(">>> [V-Thread] Contextualized Chunk successfully generated prefix: " + contextPrefix.trim());
                        }
                    } catch (Exception e) {
                        System.err.println("Contextualization generation failed: " + e.getMessage());
                    }

                    org.springframework.ai.document.Document vectorChunk = new org.springframework.ai.document.Document(
                            childId,
                            contextualizedContent,
                            new HashMap<>(child.getMetadata())
                    );

                    vectorChunk.getMetadata().put("document_id", savedDocument.getId());
                    vectorChunk.getMetadata().put("department_owner", departmentOwner);
                    vectorChunk.getMetadata().put("required_role", requiredRole);

                    vectorStoreChunks.add(vectorChunk);

                    DocumentChunk dbChunk = DocumentChunk.builder()
                            .document(savedDocument)
                            .chunkIndex(index)
                            .content(contextualizedContent)
                            .parentContent(parentText)
                            .embeddingId(childId)
                            .build();
                    dbChunksToSave.add(dbChunk);
                    return null;
                }));
            }
        }

        // Wait for concurrency completion
        for (Future<Void> future : futures) {
            try {
                future.get();
            } catch (Exception e) {
                System.err.println("Worker thread error: " + e.getMessage());
            }
        }
        executor.shutdown();

        // Sort mappings by index to maintain original structural layout
        dbChunksToSave.sort(Comparator.comparing(DocumentChunk::getChunkIndex));

        // 5. Ingest child vectors in pgvector (outside transaction)
        vectorStore.add(vectorStoreChunks);

        // 6. Persist text blocks and mapping IDs in SQL using a batch write inside a short transactional block
        transactionTemplate.executeWithoutResult(status -> {
            documentChunkRepository.saveAll(dbChunksToSave);
        });

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
