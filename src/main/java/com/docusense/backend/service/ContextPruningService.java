package com.docusense.backend.service;

import org.springframework.stereotype.Service;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Service
public class ContextPruningService {

    private static final Pattern SENTENCE_PATTERN = Pattern.compile("(?<=[.!?])\\s+");

    public String prune(String context, String query) {
        if (context == null || context.isBlank() || query == null || query.isBlank()) {
            return context;
        }

        Set<String> queryTokens = tokenize(query);
        if (queryTokens.isEmpty()) {
            return context;
        }

        String[] sentences = SENTENCE_PATTERN.split(context);
        List<String> preservedSentences = new ArrayList<>();

        for (String sentence : sentences) {
            Set<String> sentenceTokens = tokenize(sentence);
            double jaccard = calculateJaccardSimilarity(queryTokens, sentenceTokens);

            // Retain sentence if similarity score exceeds threshold or if it is short (headings)
            if (jaccard > 0.05 || sentenceTokens.size() < 5) {
                preservedSentences.add(sentence.trim());
            }
        }

        if (preservedSentences.isEmpty()) {
            return context;
        }

        return String.join(" ", preservedSentences);
    }

    private Set<String> tokenize(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptySet();
        }
        return Arrays.stream(text.toLowerCase().replaceAll("[^a-zA-Z0-9\\s]", "").split("\\s+"))
                .filter(token -> !token.isBlank() && token.length() > 2)
                .collect(Collectors.toSet());
    }

    private double calculateJaccardSimilarity(Set<String> setA, Set<String> setB) {
        if (setA.isEmpty() || setB.isEmpty()) {
            return 0.0;
        }
        Set<String> intersection = new HashSet<>(setA);
        intersection.retainAll(setB);

        Set<String> union = new HashSet<>(setA);
        union.addAll(setB);

        return (double) intersection.size() / union.size();
    }
}
