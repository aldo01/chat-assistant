package com.company.chatassistant.provider;

import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.anthropic.api.AnthropicApi;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.ai.openai.api.OpenAiApi;
import reactor.core.publisher.Flux;

/**
 * A {@link ChatModel} whose real implementation (OpenAI/ChatGPT or Anthropic/Claude)
 * is constructed at runtime from the user-supplied {@link ProviderConfig} and rebuilt
 * whenever the config changes. No API key is needed to create this bean, so the app
 * boots keyless and becomes usable the moment a key is configured.
 */
public class ProviderChatModel implements ChatModel {

    private final ProviderConfigStore store;

    private volatile ChatModel delegate;
    private volatile long builtVersion = -1;

    public ProviderChatModel(ProviderConfigStore store) {
        this.store = store;
    }

    private synchronized ChatModel delegate() {
        long current = store.version();
        if (current != builtVersion) {
            this.delegate = build();
            this.builtVersion = current;
        }
        if (delegate == null) {
            throw new ProviderNotConfiguredException(
                    "No chat provider configured. Set a chat API key via /admin.html or POST /admin/config.");
        }
        return delegate;
    }

    private ChatModel build() {
        ProviderConfig cfg = store.get();
        if (!cfg.chatConfigured()) return null;
        return switch (cfg.getChatProvider() == null ? "openai" : cfg.getChatProvider().toLowerCase()) {
            case "anthropic", "claude" -> buildAnthropic(cfg);
            default -> buildOpenAi(cfg);
        };
    }

    private ChatModel buildOpenAi(ProviderConfig cfg) {
        OpenAiApi api = cfg.getChatBaseUrl() == null || cfg.getChatBaseUrl().isBlank()
                ? new OpenAiApi(cfg.getChatApiKey())
                : new OpenAiApi(cfg.getChatBaseUrl(), cfg.getChatApiKey());
        OpenAiChatOptions opts = OpenAiChatOptions.builder()
                .withModel(cfg.getChatModel())
                .withTemperature(cfg.getChatTemperature())
                .build();
        return new OpenAiChatModel(api, opts);
    }

    private ChatModel buildAnthropic(ProviderConfig cfg) {
        AnthropicApi api = cfg.getChatBaseUrl() == null || cfg.getChatBaseUrl().isBlank()
                ? new AnthropicApi(cfg.getChatApiKey())
                : new AnthropicApi(cfg.getChatBaseUrl(), cfg.getChatApiKey());
        AnthropicChatOptions opts = AnthropicChatOptions.builder()
                .withModel(cfg.getChatModel())
                .withMaxTokens(cfg.getChatMaxTokens())
                .withTemperature(cfg.getChatTemperature())
                .build();
        return new AnthropicChatModel(api, opts);
    }

    @Override
    public ChatResponse call(Prompt prompt) {
        return delegate().call(prompt);
    }

    @Override
    public Flux<ChatResponse> stream(Prompt prompt) {
        return delegate().stream(prompt);
    }

    @Override
    public ChatOptions getDefaultOptions() {
        ProviderConfig cfg = store.get();
        if (!cfg.chatConfigured()) {
            return ChatOptionsBuilder.builder().build();
        }
        return delegate().getDefaultOptions();
    }
}
