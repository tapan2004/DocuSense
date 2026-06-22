package com.docusense.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "semantic_cache")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SemanticCache {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String query;

    @Column(name = "query_vector", columnDefinition = "vector(768)", nullable = false)
    private String queryVector; // Vector format stored in pgvector

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer;

    @Column(name = "department_scope", nullable = false)
    private String departmentScope;

    @Column(name = "role_scope", nullable = false)
    private String roleScope;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}