package com.docusense.backend.controller;

import com.docusense.backend.model.QueryFeedback;
import com.docusense.backend.model.QueryLog;
import com.docusense.backend.repository.QueryFeedbackRepository;
import com.docusense.backend.repository.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/observability")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('ROLE_ADMIN', 'ROLE_MANAGER')")
public class ObservabilityController {

    private final QueryLogRepository queryLogRepository;
    private final QueryFeedbackRepository queryFeedbackRepository;

    @GetMapping("/stats")
    public ResponseEntity<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalQueries", queryLogRepository.countAllQueries());
        stats.put("totalCost", queryLogRepository.sumAllCosts());
        stats.put("totalTokens", queryLogRepository.sumAllTokens());
        stats.put("averageLatency", queryLogRepository.averageLatency());
        
        Double hitRate = queryLogRepository.cacheHitRate();
        stats.put("cacheHitRate", hitRate != null ? hitRate : 0.0);

        Double feedbackRate = queryFeedbackRepository.positiveFeedbackRate();
        stats.put("positiveFeedbackRate", feedbackRate != null ? feedbackRate : 0.0);

        return ResponseEntity.ok(stats);
    }

    @GetMapping("/logs")
    public ResponseEntity<List<QueryLog>> getLogs() {
        List<QueryLog> logs = queryLogRepository.findTop50ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(logs);
    }

    @GetMapping("/feedbacks")
    public ResponseEntity<List<QueryFeedback>> getFeedbacks() {
        List<QueryFeedback> feedbacks = queryFeedbackRepository.findTop50ByOrderByCreatedAtDesc();
        return ResponseEntity.ok(feedbacks);
    }
}
