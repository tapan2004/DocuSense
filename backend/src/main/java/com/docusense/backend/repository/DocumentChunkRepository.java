package com.docusense.backend.repository;

import com.docusense.backend.model.DocumentChunk;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentChunkRepository extends JpaRepository<DocumentChunk, Long> {
    List<DocumentChunk> findByDocumentId(Long documentId);

    @Query("SELECT dc FROM DocumentChunk dc WHERE " +
           "(LOWER(dc.content) LIKE LOWER(CONCAT('%', :query, '%')) " +
           "OR LOWER(dc.document.title) LIKE LOWER(CONCAT('%', :query, '%'))) " +
           "AND (dc.document.departmentOwner = 'General' OR dc.document.departmentOwner = :dept) " +
           "AND dc.document.requiredRole IN :roles")
    List<DocumentChunk> findLexicalMatch(
        @Param("query") String query, 
        @Param("dept") String dept, 
        @Param("roles") List<String> roles
    );
}
