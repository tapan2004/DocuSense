package com.docusense.backend.service;

import com.docusense.backend.dto.SearchResponse;
import com.docusense.backend.dto.SourceDto;
import com.docusense.backend.model.DocumentChunk;
import com.docusense.backend.model.SemanticCache;
import com.docusense.backend.model.QueryLog;
import com.docusense.backend.repository.DocumentChunkRepository;
import com.docusense.backend.repository.SemanticCacheRepository;
import com.docusense.backend.repository.QueryLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final VectorStore vectorStore;
    private final SecurityFilterService securityFilterService;
    private final ChatClient chatClient;
    private final DocumentChunkRepository documentChunkRepository;
    private final SemanticCacheRepository semanticCacheRepository;
    private final QueryLogRepository queryLogRepository;
    private final PiiRedactorService piiRedactorService;
    private final EmbeddingModel embeddingModel;

    public SearchResponse secureSearch(String query) {
        long startTime = System.currentTimeMillis();
        String username = SecurityContextHolder.getContext().getAuthentication().getName();
        String userDepartment = securityFilterService.getUserDepartment();
        String userRole = securityFilterService.getUserRole();

        // 1. PII Redaction
        String redactedQuery = piiRedactorService.redact(query);

        // 2. HyDE Query Expansion
        String hypotheticalAnswer = redactedQuery;
        try {
            String hydePrompt = String.format(
                    "Write a short hypothetical paragraph answering the following question. Do not include any introductory remarks, headers, or external context. Question: %s",
                    redactedQuery
            );
            hypotheticalAnswer = chatClient.prompt()
                    .user(hydePrompt)
                    .call()
                    .content();
            if (hypotheticalAnswer == null || hypotheticalAnswer.isBlank()) {
                hypotheticalAnswer = redactedQuery;
            }
        } catch (Exception e) {
            System.err.println("HyDE expansion failed, falling back to query: " + e.getMessage());
        }

        // 3. Generate query embedding vector (using the HyDE expanded query)
        float[] queryEmbedding = embeddingModel.embed(hypotheticalAnswer);
        String sqlVectorString = vectorToSqlString(queryEmbedding);

        // 4. Perform Semantic Cache Lookup in PostgreSQL (Distance < 0.05 corresponds to >0.95 Cosine Similarity)
        try {
            Optional<SemanticCache> cachedQuery = semanticCacheRepository.findSimilarQuery(userDepartment, userRole, sqlVectorString);
            if (cachedQuery.isPresent()) {
                System.out.println(">>> Semantic Cache HIT for query: " + query);
                long latency = System.currentTimeMillis() - startTime;
                logQuery(username, redactedQuery, 0, 0, 0.0, latency, "HIT");
                return SearchResponse.builder()
                        .answer(cachedQuery.get().getAnswer())
                        .sources(new ArrayList<>())
                        .build();
            }
        } catch (Exception e) {
            System.err.println("Semantic Cache lookup failed: " + e.getMessage());
        }

        // 5. Retrieve security roles list
        List<String> allowedRoles = new ArrayList<>();
        allowedRoles.add("ROLE_USER");
        if ("ROLE_MANAGER".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
        } else if ("ROLE_ADMIN".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
            allowedRoles.add("ROLE_ADMIN");
        }

        // 6. Execute Hybrid Search
        // A. Semantic Search (pgvector)
        Filter.Expression securityFilter = securityFilterService.getSecureFilterExpression();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(hypotheticalAnswer)
                .topK(8) // Retrieve top 8 candidates for re-ranking
                .similarityThreshold(0.4)
                .filterExpression(securityFilter)
                .build();
        List<Document> vectorDocs = vectorStore.similaritySearch(searchRequest);

        // B. Lexical Search (SQL keyword match)
        List<DocumentChunk> lexicalMatches = documentChunkRepository.findLexicalMatch(redactedQuery, userDepartment, allowedRoles);

        // 7. Merge & Deduplicate Results
        Map<String, String> resolvedParentContext = new HashMap<>();
        Map<String, String> snippetToParentMap = new HashMap<>();
        List<SourceDto> candidates = new ArrayList<>();

        // Add Vector Docs
        for (Document doc : vectorDocs) {
            String embeddingId = doc.getId();
            Double score = (Double) doc.getMetadata().getOrDefault("distance", 0.0);
            
            Optional<DocumentChunk> chunkOpt = documentChunkRepository.findAll().stream()
                    .filter(c -> c.getEmbeddingId().equals(embeddingId))
                    .findFirst();

            if (chunkOpt.isPresent()) {
                DocumentChunk chunk = chunkOpt.get();
                resolvedParentContext.put(embeddingId, chunk.getParentContent());
                snippetToParentMap.put(chunk.getContent(), chunk.getParentContent());
                candidates.add(SourceDto.builder()
                        .documentId(chunk.getDocument().getId())
                        .title(chunk.getDocument().getTitle())
                        .similarityScore(1.0 - score)
                        .textSnippet(chunk.getContent())
                        .build());
            }
        }

        // Add Lexical Matches
        for (DocumentChunk chunk : lexicalMatches) {
            if (!resolvedParentContext.containsKey(chunk.getEmbeddingId())) {
                resolvedParentContext.put(chunk.getEmbeddingId(), chunk.getParentContent());
                snippetToParentMap.put(chunk.getContent(), chunk.getParentContent());
                candidates.add(SourceDto.builder()
                        .documentId(chunk.getDocument().getId())
                        .title(chunk.getDocument().getTitle())
                        .similarityScore(0.85)
                        .textSnippet(chunk.getContent())
                        .build());
            }
        }

        if (candidates.isEmpty()) {
            long latency = System.currentTimeMillis() - startTime;
            logQuery(username, redactedQuery, 0, 0, 0.0, latency, "MISS");
            return SearchResponse.builder()
                    .answer("I could not find any documents containing information relevant to your query that you are authorized to view.")
                    .sources(new ArrayList<>())
                    .build();
        }

        // 8. LLM Re-ranking Layer
        List<SourceDto> reRankedSources = new ArrayList<>();
        if (candidates.size() > 4) {
            try {
                StringBuilder rankPrompt = new StringBuilder();
                rankPrompt.append("You are a search ranking assistant. Rank the following context snippets based on their relevance to answering the user query.\n\n");
                rankPrompt.append("QUERY: ").append(redactedQuery).append("\n\n");
                rankPrompt.append("SNIPPETS:\n");
                for (int i = 0; i < candidates.size(); i++) {
                    rankPrompt.append("INDEX: ").append(i).append("\n");
                    rankPrompt.append("TEXT: ").append(candidates.get(i).getTextSnippet()).append("\n---\n");
                }
                rankPrompt.append("\nOutput a list of selected snippet INDEX numbers that are highly relevant, in order of descending relevance, separated by commas (e.g., '2,0,5,1'). Do not include any other text or explanation.");

                String rankResponse = chatClient.prompt()
                        .user(rankPrompt.toString())
                        .call()
                        .content();

                if (rankResponse != null && !rankResponse.isBlank()) {
                    String[] parts = rankResponse.replaceAll("[^0-9,]", "").split(",");
                    for (String part : parts) {
                        if (!part.isBlank()) {
                            int idx = Integer.parseInt(part.trim());
                            if (idx >= 0 && idx < candidates.size()) {
                                reRankedSources.add(candidates.get(idx));
                                if (reRankedSources.size() >= 4) {
                                    break;
                                }
                            }
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("LLM re-ranking failed: " + e.getMessage());
            }
        }

        // Fallback to top 4 if re-ranking failed or returned no results
        if (reRankedSources.isEmpty()) {
            reRankedSources = candidates.stream()
                    .sorted((a, b) -> Double.compare(b.getSimilarityScore(), a.getSimilarityScore()))
                    .limit(4)
                    .collect(Collectors.toList());
        }

        // 9. Aggregate Parent Context segments
        List<String> contextParts = new ArrayList<>();
        for (SourceDto src : reRankedSources) {
            String parent = snippetToParentMap.get(src.getTextSnippet());
            if (parent != null) {
                contextParts.add(parent);
            } else {
                contextParts.add(src.getTextSnippet());
            }
        }
        String aggregatedContext = String.join("\n\n---\n\n", contextParts);

        // 10. Query Gemini for Response (Using standard ChatResponse for Token Tracking)
        String combinedPrompt = String.format("""
                You are a secure corporate AI assistant. You answer questions using only the provided document snippets under CONTEXT.
                
                Rules:
                1. Answer the query relying exclusively on the facts stated in the CONTEXT.
                2. Do not use external knowledge or invent facts.
                3. If the answer cannot be found in the CONTEXT, reply exactly: "I do not have access to that information or it is not available in my authorized documents."
                
                CONTEXT:
                %s
                
                QUESTION:
                %s
                """, aggregatedContext, redactedQuery);

        ChatResponse chatResponse = chatClient.prompt()
                .user(combinedPrompt)
                .call()
                .chatResponse();

        String generatedAnswer = chatResponse.getResult().getOutput().getContent();

        // Extract token usage and cost metrics
        Usage usage = chatResponse.getMetadata().getUsage();
        int inputTokens = (usage != null && usage.getPromptTokens() != null) ? usage.getPromptTokens().intValue() : 0;
        int outputTokens = (usage != null && usage.getGenerationTokens() != null) ? usage.getGenerationTokens().intValue() : 0;
        double cost = (inputTokens * 0.075 / 1_000_000.0) + (outputTokens * 0.30 / 1_000_000.0);
        long latency = System.currentTimeMillis() - startTime;

        // Save log details to DB
        logQuery(username, redactedQuery, inputTokens, outputTokens, cost, latency, "MISS");

        // 11. Groundedness Evaluation Guardrail
        if (generatedAnswer != null && !generatedAnswer.contains("I do not have access")) {
            String validationPrompt = String.format("""
                You are an AI safety evaluator. 
                Review the following generated ANSWER against the verified CONTEXT.
                Determine if the ANSWER contains any facts or statements that are NOT supported by the CONTEXT.
                If there are any hallucinations or unsupported claims, output: 'UNGROUNDED'.
                If all facts in the ANSWER are completely supported by the CONTEXT, output: 'GROUNDED'.
                Do not include any other text in your response.
                
                CONTEXT:
                %s
                
                ANSWER:
                %s
                """, aggregatedContext, generatedAnswer);

            try {
                String evaluation = chatClient.prompt()
                        .user(validationPrompt)
                        .call()
                        .content();

                if (evaluation != null && evaluation.trim().equalsIgnoreCase("UNGROUNDED")) {
                    System.out.println(">>> Guardrail Triggered: Generated answer was evaluated as UNGROUNDED.");
                    generatedAnswer += "\n\n*(Note: This response contains details flagged by the guardrails as potentially ungrounded based on current document context.)*";
                }
            } catch (Exception e) {
                System.err.println("Groundedness evaluation failed: " + e.getMessage());
            }
        }

        // 12. Write Response to Semantic Cache Table
        if (generatedAnswer != null) {
            try {
                SemanticCache newCache = SemanticCache.builder()
                        .query(redactedQuery)
                        .queryVector(sqlVectorString)
                        .answer(generatedAnswer)
                        .departmentScope(userDepartment)
                        .roleScope(userRole)
                        .build();
                semanticCacheRepository.save(newCache);
            } catch (Exception e) {
                System.err.println("Failed to write to semantic cache: " + e.getMessage());
            }
        }

        return SearchResponse.builder()
                .answer(generatedAnswer)
                .sources(reRankedSources)
                .build();
    }

    private void logQuery(String username, String query, int inputTokens, int outputTokens, double cost, long latency, String cacheStatus) {
        try {
            QueryLog log = QueryLog.builder()
                    .username(username)
                    .query(query)
                    .inputTokens(inputTokens)
                    .outputTokens(outputTokens)
                    .estimatedCost(cost)
                    .latencyMs(latency)
                    .cacheStatus(cacheStatus)
                    .build();
            queryLogRepository.save(log);
        } catch (Exception e) {
            System.err.println("Failed to save query log: " + e.getMessage());
        }
    }

    private String vectorToSqlString(float[] vector) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < vector.length; i++) {
            sb.append(vector[i]);
            if (i < vector.length - 1) {
                sb.append(",");
            }
        }
        sb.append("]");
        return sb.toString();
    }
}
