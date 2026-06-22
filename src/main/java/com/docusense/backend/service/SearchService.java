package com.docusense.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final VectorStore vectorStore;
    private final SecurityFilterService securityFilterService;
    private final ChatClient chatClient;
    private final RedisTemplate<String, Object> redisTemplate;

    public String secureSearch(String query) {
        // 1. Resolve security credentials and construct cache key
        String userDepartment = securityFilterService.getUserDepartment();
        String userRole = securityFilterService.getUserRole();
        String cacheKey = getSecureCacheKey(userDepartment, userRole, query);

        // 2. Attempt cache lookup
        try {
            String cachedResponse = (String) redisTemplate.opsForValue().get(cacheKey);
            if (cachedResponse != null) {
                return cachedResponse;
            }
        } catch (Exception e) {
            // Fail-silent: log error and query database/LLM directly if Redis is down
            System.err.println("Redis cache lookup failed: " + e.getMessage());
        }

        // 3. Get secure pre-retrieval filter expression based on user permissions
        Filter.Expression securityFilter = securityFilterService.getSecureFilterExpression();

        // 4. Query Vector Database with the security filter
        SearchRequest searchRequest = SearchRequest.builder()
                .query(query)
                .topK(5)
                .similarityThreshold(0.5)
                .filterExpression(securityFilter)
                .build();

        List<Document> documents = vectorStore.similaritySearch(searchRequest);
        if (documents.isEmpty()) {
            return "I could not find any documents containing information relevant to your query that you are authorized to view.";
        }

        // 5. Extract and combine parsed text contents from the chunks
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 6. Query the LLM using a single, unified user prompt to avoid Gemini system role serialization issues
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
                """, context, query);

        String answer = chatClient.prompt()
                .user(combinedPrompt)
                .call()
                .content();

        // 7. Write generated response back to cache (TTL: 10 minutes)
        if (answer != null) {
            try {
                redisTemplate.opsForValue().set(cacheKey, answer, Duration.ofMinutes(10));
            } catch (Exception e) {
                System.err.println("Redis cache write failed: " + e.getMessage());
            }
        }
        return answer;
    }

    private String getSecureCacheKey(String userDepartment, String userRole, String query) {
        try {
            String combinedString = userDepartment + ":" + userRole + ":" + query;
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(combinedString.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return "docusense:cache:" + hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return "docusense:cache:" + userDepartment + ":" + userRole + ":" + query.hashCode();
        }
    }
}