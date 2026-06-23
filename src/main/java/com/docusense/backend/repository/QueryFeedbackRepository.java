package com.docusense.backend.repository;

import com.docusense.backend.model.QueryFeedback;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface QueryFeedbackRepository extends JpaRepository<QueryFeedback, Long> {
    List<QueryFeedback> findTop50ByOrderByCreatedAtDesc();

    Page<QueryFeedback> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query(value = "SELECT (COUNT(CASE WHEN rating = 1 THEN 1 END) * 100.0) / NULLIF(COUNT(*), 0) FROM query_feedbacks", nativeQuery = true)
    Double positiveFeedbackRate();
}
