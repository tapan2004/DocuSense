package com.docusense.backend.repository;

import com.docusense.backend.model.Document;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface DocumentRepository extends JpaRepository<Document, Long> {

    @Query("SELECT d FROM Document d WHERE (LOWER(d.departmentOwner) = 'general' OR LOWER(d.departmentOwner) = LOWER(:department)) AND d.requiredRole IN :roles")
    List<Document> findAuthorizedDocuments(@Param("department") String department, @Param("roles") List<String> roles);
}
