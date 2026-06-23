package com.docusense.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "document_chunks", indexes = {
    @Index(name = "idx_doc_chunk_doc_id", columnList = "document_id"),
    @Index(name = "idx_doc_chunk_embedding_id", columnList = "embedding_id")
})
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DocumentChunk {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "document_id", nullable = false)
    private Document document;

    @Column(name = "chunk_index", nullable = false)
    private Integer chunkIndex;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String content; // Child chunk text

    @Column(name = "parent_content", columnDefinition = "TEXT", nullable = false)
    private String parentContent; // Wider parent segment context (1200 tokens)

    @Column(name = "embedding_id", nullable = false)
    private String embeddingId;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}