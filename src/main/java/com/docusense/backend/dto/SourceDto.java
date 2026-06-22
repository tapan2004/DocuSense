package com.docusense.backend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SourceDto {
    private Long documentId;
    private String title;
    private Double similarityScore;
    private String textSnippet;
}