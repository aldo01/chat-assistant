package com.company.chatassistant.provider;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.openai.OpenAiEmbeddingModel;
import org.springframework.ai.openai.OpenAiEmbeddingOptions;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * An {@link EmbeddingModel} whose real implementation is built at runtime from the
 * user-supplied {@link ProviderConfig}. It rebuilds whenever the config version changes.
 *
 * Crucially, {@link #dimensions()} returns the configured constant and never calls the
 * network — this is what lets the app (and the Qdrant vector store) boot with no API key.
 */
public class ProviderEmbeddingModel extends AbstractEmbeddingModel {

    private final ProviderConfigStore store;

    private volatile EmbeddingModel delegate;
    private volatile long builtVersion = -1;

    public ProviderEmbeddingModel(ProviderConfigStore store) {
        this.store = store;
    }

    private synchronized EmbeddingModel delegate() {
        long current = store.version();
        if (current != builtVersion) {
            this.delegate = build();
            this.builtVersion = current;
        }
        if (delegate == null) {
            throw new ProviderNotConfiguredException(
                    "No embeddings provider configured. Set an embeddings API key via /admin.html "
                    + "or POST /admin/config (Anthropic has no embeddings API — use OpenAI or a local model).");
        }
        return delegate;
    }

    private EmbeddingModel build() {
        ProviderConfig cfg = store.get();
        if (!cfg.embeddingConfigured()) return null;
        OpenAiApi api = cfg.getEmbeddingBaseUrl() == null || cfg.getEmbeddingBaseUrl().isBlank()
                ? new OpenAiApi(cfg.getEmbeddingApiKey())
                : new OpenAiApi(cfg.getEmbeddingBaseUrl(), cfg.getEmbeddingApiKey());
        OpenAiEmbeddingOptions opts = OpenAiEmbeddingOptions.builder()
                .withModel(cfg.getEmbeddingModel())
                .build();
        return new OpenAiEmbeddingModel(api, MetadataMode.EMBED, opts);
    }

    @Override
    public int dimensions() {
        return store.get().getEmbeddingDimensions();
    }

    @Override
    public EmbeddingResponse call(EmbeddingRequest request) {
        return delegate().call(request);
    }

    @Override
    public float[] embed(Document document) {
        return delegate().embed(document);
    }
}
