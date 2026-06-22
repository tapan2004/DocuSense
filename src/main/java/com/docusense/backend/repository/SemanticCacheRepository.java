package com.docusense.backend.repository;

import com.docusense.backend.model.SemanticCache;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.Optional;

@Repository
public interface SemanticCacheRepository extends JpaRepository<SemanticCache, Long> {

    @Query(value = "SELECT * FROM semantic_cache " +
            "WHERE department_scope = :dept AND role_scope = :role " +
            "ORDER BY query_vector <=> cast(:vector as vector) LIMIT 1", nativeQuery = true)
    Optional<SemanticCache> findSimilarQuery(
            @Param("dept") String dept,
            @Param("role") String role,
            @Param("vector") String vectorString);
}
