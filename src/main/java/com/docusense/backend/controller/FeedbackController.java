package com.docusense.backend.controller;

import com.docusense.backend.model.QueryFeedback;
import com.docusense.backend.repository.QueryFeedbackRepository;
import com.docusense.backend.service.PiiRedactorService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/feedback")
@RequiredArgsConstructor
public class FeedbackController {

    private final QueryFeedbackRepository queryFeedbackRepository;
    private final PiiRedactorService piiRedactorService;

    @PostMapping
    public ResponseEntity<String> saveFeedback(@RequestBody QueryFeedback feedback) {
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        feedback.setUsername(username);
        
        // Redact any PII in the feedback query/answer before saving
        feedback.setQuery(piiRedactorService.redact(feedback.getQuery()));
        feedback.setAnswer(piiRedactorService.redact(feedback.getAnswer()));

        queryFeedbackRepository.save(feedback);
        return ResponseEntity.ok("Feedback recorded successfully.");
    }
}
