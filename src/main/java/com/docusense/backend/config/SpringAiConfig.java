package com.docusense.backend.config;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SpringAiConfig {

    /**
     * Configures a thread-safe ChatClient bean with a default system prompt.
     * Spring Boot automatically injects the autoconfigured ChatClient.Builder.
     */
    @Bean
    public ChatClient chatClient(ChatClient.Builder builder) {
        return builder
                .defaultSystem("You are a secure corporate AI assistant for DocuSense.")
                .build();
    }
}
