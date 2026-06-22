package com.docusense.backend.model;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Entity
@Table(name = "query_feedbacks")
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class QueryFeedback {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String username;

    @Column(columnDefinition = "TEXT", nullable = false)
    private String query; // Redacted query string

    @Column(columnDefinition = "TEXT", nullable = false)
    private String answer; // Generated answer

    @Column(nullable = false)
    private Integer rating; // 1 for thumbs up, -1 for thumbs down

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
    }
}
