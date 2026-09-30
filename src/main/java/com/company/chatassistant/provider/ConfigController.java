package com.company.chatassistant.provider;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Runtime model configuration API — the "bring your own key" control plane.
 *
 *   GET  /admin/config        current config (keys masked)
 *   PUT  /admin/config        set provider(s) + key(s); blank key fields keep the existing key
 *   POST /admin/config/test   validate the configured keys with a tiny live call
 *
 * Served alongside the static settings page at /admin.html (same origin).
 * PROTECT THIS WITH AUTH in production — it stores provider API keys.
 */
@RestController
@RequestMapping("/admin/config")
public class ConfigController {

    private final ProviderConfigStore store;
    private final ChatModel chatModel;
    private final EmbeddingModel embeddingModel;

    public ConfigController(ProviderConfigStore store, ChatModel chatModel, EmbeddingModel embeddingModel) {
        this.store = store;
        this.chatModel = chatModel;
        this.embeddingModel = embeddingModel;
    }

    @GetMapping
    public Map<String, Object> current() {
        return masked(store.get());
    }

    @PutMapping
    public Map<String, Object> update(@RequestBody ProviderConfig incoming) {
        ProviderConfig cur = store.get();
        // Keep existing keys if the caller left the key fields blank.
        if (incoming.getChatApiKey() == null || incoming.getChatApiKey().isBlank()) {
            incoming.setChatApiKey(cur.getChatApiKey());
        }
        if (incoming.getEmbeddingApiKey() == null || incoming.getEmbeddingApiKey().isBlank()) {
            incoming.setEmbeddingApiKey(cur.getEmbeddingApiKey());
        }
        return masked(store.update(incoming));
    }

    @PostMapping("/test")
    public Map<String, Object> test() {
        Map<String, Object> result = new LinkedHashMap<>();

        boolean chatOk = false;
        String chatError = null;
        try {
            String reply = chatModel.call("Reply with the single word: ok");
            chatOk = reply != null && !reply.isBlank();
        } catch (Exception e) {
            chatError = rootMessage(e);
        }
        result.put("chatOk", chatOk);
        result.put("chatError", chatError);

        boolean embeddingOk = false;
        String embeddingError = null;
        try {
            float[] v = embeddingModel.embed("connection test");
            embeddingOk = v != null && v.length > 0;
            result.put("embeddingDimensions", v == null ? 0 : v.length);
        } catch (Exception e) {
            embeddingError = rootMessage(e);
        }
        result.put("embeddingOk", embeddingOk);
        result.put("embeddingError", embeddingError);

        return result;
    }

    private Map<String, Object> masked(ProviderConfig c) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("chatProvider", c.getChatProvider());
        m.put("chatModel", c.getChatModel());
        m.put("chatBaseUrl", c.getChatBaseUrl());
        m.put("chatTemperature", c.getChatTemperature());
        m.put("chatMaxTokens", c.getChatMaxTokens());
        m.put("chatConfigured", c.chatConfigured());
        m.put("chatKeyLast4", last4(c.getChatApiKey()));

        m.put("embeddingProvider", c.getEmbeddingProvider());
        m.put("embeddingModel", c.getEmbeddingModel());
        m.put("embeddingBaseUrl", c.getEmbeddingBaseUrl());
        m.put("embeddingDimensions", c.getEmbeddingDimensions());
        m.put("embeddingConfigured", c.embeddingConfigured());
        m.put("embeddingKeyLast4", last4(c.getEmbeddingApiKey()));

        m.put("brand", c.getBrand());
        return m;
    }

    private static String last4(String key) {
        if (key == null || key.length() < 4) return "";
        return key.substring(key.length() - 4);
    }

    private static String rootMessage(Throwable t) {
        Throwable r = t;
        while (r.getCause() != null && r.getCause() != r) r = r.getCause();
        String msg = r.getMessage();
        return msg == null ? r.getClass().getSimpleName() : msg;
    }
}
