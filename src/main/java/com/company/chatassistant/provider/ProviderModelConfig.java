package com.company.chatassistant.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Wires the provider-agnostic {@link ChatModel} and {@link EmbeddingModel} beans.
 *
 * These replace Spring AI's auto-configured models (which require a key at boot).
 * The OpenAI/Anthropic auto-configurations are excluded in application.yml.
 */
@Configuration
public class ProviderModelConfig {

    @Bean
    @Primary
    public EmbeddingModel embeddingModel(ProviderConfigStore store) {
        return new ProviderEmbeddingModel(store);
    }

    @Bean
    @Primary
    public ChatModel chatModel(ProviderConfigStore store) {
        return new ProviderChatModel(store);
    }
}
