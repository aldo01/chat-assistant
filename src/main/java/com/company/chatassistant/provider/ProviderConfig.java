package com.company.chatassistant.provider;

/**
 * Runtime, user-supplied model configuration.
 *
 * This is what lets anyone "bring their own key": the app boots with no key,
 * and the user POSTs their provider + key to /admin/config at runtime.
 *
 * Chat can be OpenAI (ChatGPT) or Anthropic (Claude).
 * Embeddings must come from an embeddings-capable provider (OpenAI / any
 * OpenAI-compatible endpoint) because Anthropic does not offer an embeddings API.
 */
public class ProviderConfig {

    // ---- chat (answer generation) ----
    private String chatProvider = "openai";          // openai | anthropic
    private String chatModel = "gpt-4o-mini";
    private String chatApiKey = "";
    private String chatBaseUrl = "";                 // optional override
    private double chatTemperature = 0.2;
    private int chatMaxTokens = 1024;                // required by Anthropic

    // ---- embeddings (retrieval) ----
    private String embeddingProvider = "openai";     // openai (or openai-compatible)
    private String embeddingModel = "text-embedding-3-small";
    private String embeddingApiKey = "";
    private String embeddingBaseUrl = "";            // optional override
    private int embeddingDimensions = 1536;          // must match the Qdrant collection

    // ---- presentation ----
    private String brand = "Our Company";

    public String getChatProvider() { return chatProvider; }
    public void setChatProvider(String v) { this.chatProvider = v; }
    public String getChatModel() { return chatModel; }
    public void setChatModel(String v) { this.chatModel = v; }
    public String getChatApiKey() { return chatApiKey; }
    public void setChatApiKey(String v) { this.chatApiKey = v; }
    public String getChatBaseUrl() { return chatBaseUrl; }
    public void setChatBaseUrl(String v) { this.chatBaseUrl = v; }
    public double getChatTemperature() { return chatTemperature; }
    public void setChatTemperature(double v) { this.chatTemperature = v; }
    public int getChatMaxTokens() { return chatMaxTokens; }
    public void setChatMaxTokens(int v) { this.chatMaxTokens = v; }

    public String getEmbeddingProvider() { return embeddingProvider; }
    public void setEmbeddingProvider(String v) { this.embeddingProvider = v; }
    public String getEmbeddingModel() { return embeddingModel; }
    public void setEmbeddingModel(String v) { this.embeddingModel = v; }
    public String getEmbeddingApiKey() { return embeddingApiKey; }
    public void setEmbeddingApiKey(String v) { this.embeddingApiKey = v; }
    public String getEmbeddingBaseUrl() { return embeddingBaseUrl; }
    public void setEmbeddingBaseUrl(String v) { this.embeddingBaseUrl = v; }
    public int getEmbeddingDimensions() { return embeddingDimensions; }
    public void setEmbeddingDimensions(int v) { this.embeddingDimensions = v; }

    public String getBrand() { return brand; }
    public void setBrand(String v) { this.brand = v; }

    public boolean chatConfigured() { return chatApiKey != null && !chatApiKey.isBlank(); }
    public boolean embeddingConfigured() { return embeddingApiKey != null && !embeddingApiKey.isBlank(); }
}
