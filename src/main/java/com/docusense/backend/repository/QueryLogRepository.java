package com.docusense.backend.repository;

import com.docusense.backend.model.QueryLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QueryLogRepository extends JpaRepository<QueryLog, Long> {
    List<QueryLog> findTop50ByOrderByCreatedAtDesc();

    @Query(value = "SELECT COUNT(*) FROM query_logs", nativeQuery = true)
    long countAllQueries();

    @Query(value = "SELECT COALESCE(SUM(estimated_cost), 0.0) FROM query_logs", nativeQuery = true)
    double sumAllCosts();

    @Query(value = "SELECT COALESCE(SUM(input_tokens + output_tokens), 0) FROM query_logs", nativeQuery = true)
    long sumAllTokens();

    @Query(value = "SELECT COALESCE(AVG(latency_ms), 0.0) FROM query_logs", nativeQuery = true)
    double averageLatency();

    @Query(value = "SELECT (COUNT(CASE WHEN cache_status = 'HIT' THEN 1 END) * 100.0) / NULLIF(COUNT(*), 0) FROM query_logs", nativeQuery = true)
    Double cacheHitRate();
}
