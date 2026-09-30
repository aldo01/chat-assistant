package com.company.chatassistant.provider;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Holds the current {@link ProviderConfig} in memory and persists it to a local
 * JSON file so the chosen provider + key survive restarts.
 *
 * The file contains the API key in plaintext — it is meant for local/self-hosted
 * use and is git-ignored. In a hosted product, back this with a secret manager.
 *
 * A monotonically increasing {@code version} lets the delegating model beans know
 * when to rebuild their underlying provider client.
 */
@Component
public class ProviderConfigStore {

    private static final Logger log = LoggerFactory.getLogger(ProviderConfigStore.class);

    private final ObjectMapper mapper = new ObjectMapper();
    private final Path file;
    private final String seedOpenAiKey;

    private volatile ProviderConfig config = new ProviderConfig();
    private final AtomicLong version = new AtomicLong(0);

    public ProviderConfigStore(
            @Value("${app.config-file:config/provider-config.json}") String configFile,
            @Value("${OPENAI_API_KEY:}") String seedOpenAiKey) {
        this.file = Path.of(configFile);
        this.seedOpenAiKey = seedOpenAiKey;
    }

    @PostConstruct
    void load() {
        if (Files.exists(file)) {
            try {
                config = mapper.readValue(Files.readAllBytes(file), ProviderConfig.class);
                version.incrementAndGet();
                log.info("Loaded provider config from {} (chat={}, embedding={})",
                        file, config.getChatProvider(), config.getEmbeddingProvider());
                return;
            } catch (IOException e) {
                log.warn("Could not read {}, starting from defaults: {}", file, e.getMessage());
            }
        }
        // Seed from env so an existing OPENAI_API_KEY still works out of the box.
        if (seedOpenAiKey != null && !seedOpenAiKey.isBlank()) {
            config.setChatApiKey(seedOpenAiKey);
            config.setEmbeddingApiKey(seedOpenAiKey);
            version.incrementAndGet();
            log.info("Seeded provider config from OPENAI_API_KEY env var");
        } else {
            log.info("No provider configured yet — set one via POST /admin/config or /admin.html");
        }
    }

    public ProviderConfig get() { return config; }

    public long version() { return version.get(); }

    public synchronized ProviderConfig update(ProviderConfig incoming) {
        this.config = incoming;
        version.incrementAndGet();
        persist();
        log.info("Provider config updated (chat={}/{}, embedding={}/{})",
                incoming.getChatProvider(), incoming.getChatModel(),
                incoming.getEmbeddingProvider(), incoming.getEmbeddingModel());
        return config;
    }

    private void persist() {
        try {
            if (file.getParent() != null) Files.createDirectories(file.getParent());
            Files.write(file, mapper.writerWithDefaultPrettyPrinter().writeValueAsBytes(config));
        } catch (IOException e) {
            log.error("Failed to persist provider config to {}: {}", file, e.getMessage());
        }
    }
}
