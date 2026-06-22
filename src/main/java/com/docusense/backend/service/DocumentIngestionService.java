package com.docusense.backend.service;

import com.docusense.backend.model.Document;
import com.docusense.backend.repository.DocumentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.reader.tika.TikaDocumentReader;
import org.springframework.ai.transformer.splitter.TokenTextSplitter;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.core.io.InputStreamResource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.List;

@Service
@RequiredArgsConstructor
public class DocumentIngestionService {

    private final DocumentRepository documentRepository;
    private final VectorStore vectorStore;

    /**
     * Parses the file, chunks it, appends metadata security tags, and saves it.
     */
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

        document = documentRepository.save(document);

        // 2. Parse file content using Apache Tika (Spring AI wrapper)
        TikaDocumentReader tikaReader = new TikaDocumentReader(new InputStreamResource(file.getInputStream()));
        List<org.springframework.ai.document.Document> parsedDocuments = tikaReader.get();

        // 3. Chunk the document using the splitter builder to avoid deprecation warnings
        TokenTextSplitter splitter = TokenTextSplitter.builder()
                .withChunkSize(800)
                .build();
        List<org.springframework.ai.document.Document> chunks = splitter.apply(parsedDocuments);

        // 4. Populate security tags into each chunk's metadata context (Pre-Retrieval Hook)
        final Long documentId = document.getId();
        for (org.springframework.ai.document.Document chunk : chunks) {
            chunk.getMetadata().put("document_id", documentId);
            chunk.getMetadata().put("department_owner", departmentOwner);
            chunk.getMetadata().put("required_role", requiredRole);
        }

        // 5. Generate embeddings and save to PostgreSQL + pgvector
        vectorStore.add(chunks);

        return document;
    }
}
