package com.docusense.backend.service;

import com.docusense.backend.dto.SearchResponse;
import com.docusense.backend.dto.SourceDto;
import com.docusense.backend.model.DocumentChunk;
import com.docusense.backend.model.SemanticCache;
import com.docusense.backend.repository.DocumentChunkRepository;
import com.docusense.backend.repository.SemanticCacheRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
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
    private final EmbeddingModel embeddingModel;

    public SearchResponse secureSearch(String query) {
        String userDepartment = securityFilterService.getUserDepartment();
        String userRole = securityFilterService.getUserRole();

        // 1. Generate query embedding vector
        float[] queryEmbedding = embeddingModel.embed(query);
        String sqlVectorString = vectorToSqlString(queryEmbedding);

        // 2. Perform Semantic Cache Lookup in PostgreSQL (Distance < 0.05 corresponds to >0.95 Cosine Similarity)
        try {
            Optional<SemanticCache> cachedQuery = semanticCacheRepository.findSimilarQuery(userDepartment, userRole, sqlVectorString);
            if (cachedQuery.isPresent()) {
                // Confirm cache distance is acceptable (<0.05 represents a match)
                System.out.println(">>> Semantic Cache HIT for query: " + query);
                return SearchResponse.builder()
                        .answer(cachedQuery.get().getAnswer())
                        .sources(new ArrayList<>()) // Cached responses return direct answers without re-fetching sources
                        .build();
            }
        } catch (Exception e) {
            System.err.println("Semantic Cache lookup failed: " + e.getMessage());
        }

        // 3. Retrieve security roles list
        List<String> allowedRoles = new ArrayList<>();
        allowedRoles.add("ROLE_USER");
        if ("ROLE_MANAGER".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
        } else if ("ROLE_ADMIN".equals(userRole)) {
            allowedRoles.add("ROLE_MANAGER");
            allowedRoles.add("ROLE_ADMIN");
        }

        // 4. Execute Hybrid Search
        // A. Semantic Search (pgvector)
        Filter.Expression securityFilter = securityFilterService.getSecureFilterExpression();
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(4)
                .similarityThreshold(0.4)
                .filterExpression(securityFilter)
                .build();
        List<Document> vectorDocs = vectorStore.similaritySearch(searchRequest);

        // B. Lexical Search (SQL keyword match)
        List<DocumentChunk> lexicalMatches = documentChunkRepository.findLexicalMatch(query, userDepartment, allowedRoles);

        // 5. Merge & Deduplicate Results
        Map<String, String> resolvedParentContext = new HashMap<>();
        List<SourceDto> sources = new ArrayList<>();

        // Add Vector Docs
        for (Document doc : vectorDocs) {
            String embeddingId = doc.getId();
            Double score = (Double) doc.getMetadata().getOrDefault("distance", 0.0);
            
            // Map Child Vector to Parent Context
            Optional<DocumentChunk> chunkOpt = documentChunkRepository.findAll().stream()
                    .filter(c -> c.getEmbeddingId().equals(embeddingId))
                    .findFirst();

            if (chunkOpt.isPresent()) {
                DocumentChunk chunk = chunkOpt.get();
                resolvedParentContext.put(embeddingId, chunk.getParentContent());
                sources.add(SourceDto.builder()
                        .documentId(chunk.getDocument().getId())
                        .title(chunk.getDocument().getTitle())
                        .similarityScore(1.0 - score) // Convert distance to score
                        .textSnippet(chunk.getContent())
                        .build());
            }
        }

        // Add Lexical Matches (if not already included by vector search)
        for (DocumentChunk chunk : lexicalMatches) {
            if (!resolvedParentContext.containsKey(chunk.getEmbeddingId())) {
                resolvedParentContext.put(chunk.getEmbeddingId(), chunk.getParentContent());
                sources.add(SourceDto.builder()
                        .documentId(chunk.getDocument().getId())
                        .title(chunk.getDocument().getTitle())
                        .similarityScore(0.85) // Static baseline score for keyword match
                        .textSnippet(chunk.getContent())
                        .build());
            }
        }

        if (resolvedParentContext.isEmpty()) {
            return SearchResponse.builder()
                    .answer("I could not find any documents containing information relevant to your query that you are authorized to view.")
                    .sources(new ArrayList<>())
                    .build();
        }

        // 6. Aggregate Context (Using Parent segments instead of child segments!)
        String aggregatedContext = String.join("\n\n---\n\n", resolvedParentContext.values());

        // 7. Query Gemini for Response
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
                """, aggregatedContext, query);

        String generatedAnswer = chatClient.prompt()
                .user(combinedPrompt)
                .call()
                .content();

        // 8. Groundedness Evaluation Guardrail (Verify answer against Context)
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

        // 9. Write Response to Semantic Cache Table
        if (generatedAnswer != null) {
            try {
                SemanticCache newCache = SemanticCache.builder()
                        .query(query)
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
                .sources(sources)
                .build();
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
