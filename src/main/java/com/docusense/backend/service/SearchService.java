package com.docusense.backend.service;

import lombok.RequiredArgsConstructor;
import org.springframework.ai.chat.client.ChatClient; // <-- Change to ChatClient
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.ai.vectorstore.filter.Filter;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class SearchService {

    private final VectorStore vectorStore;
    private final SecurityFilterService securityFilterService;
    private final ChatClient chatClient;

    public String secureSearch(String query) {
        // 1. Get secure pre-retrieval filter expression based on user permissions
        Filter.Expression securityFilter = securityFilterService.getSecureFilterExpression();

        // 2. Query Vector Database with the security filter
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

        // 3. Extract and combine parsed text contents from the chunks
        String context = documents.stream()
                .map(Document::getText)
                .collect(Collectors.joining("\n\n---\n\n"));

        // 4. Query the LLM using ChatClient's modern fluent API
        return chatClient.prompt()
                .system(sp -> sp.text("""
                                You are a secure corporate AI assistant. You answer questions using only the provided document snippets under CONTEXT.
                                
                                Rules:
                                1. Answer the query relying exclusively on the facts stated in the CONTEXT.
                                2. Do not use external knowledge or invent facts.
                                3. If the answer cannot be found in the CONTEXT, reply exactly: "I do not have access to that information or it is not available in my authorized documents."
                                
                                CONTEXT:
                                {context}
                                """)
                        .param("context", context))
                .user(query)
                .call()
                .content();
    }
}