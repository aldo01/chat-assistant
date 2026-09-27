package com.company.chatassistant.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * All tunable knobs live here. Bound from `app.*` in application.yml.
 * Kept as a mutable class (not a record) so Spring's relaxed binding works.
 */
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    private final Ingest ingest = new Ingest();
    private final Retrieval retrieval = new Retrieval();
    private final Reranker reranker = new Reranker();
    private final Elasticsearch elasticsearch = new Elasticsearch();
    private final Cors cors = new Cors();

    public Ingest getIngest() { return ingest; }
    public Retrieval getRetrieval() { return retrieval; }
    public Reranker getReranker() { return reranker; }
    public Elasticsearch getElasticsearch() { return elasticsearch; }
    public Cors getCors() { return cors; }

    public static class Ingest {
        private String kafkaTopicRaw = "docs.raw";
        private int chunkTargetTokens = 400;
        private int chunkOverlapTokens = 60;
        private int embeddingBatchSize = 64;

        public String getKafkaTopicRaw() { return kafkaTopicRaw; }
        public void setKafkaTopicRaw(String v) { this.kafkaTopicRaw = v; }
        public int getChunkTargetTokens() { return chunkTargetTokens; }
        public void setChunkTargetTokens(int v) { this.chunkTargetTokens = v; }
        public int getChunkOverlapTokens() { return chunkOverlapTokens; }
        public void setChunkOverlapTokens(int v) { this.chunkOverlapTokens = v; }
        public int getEmbeddingBatchSize() { return embeddingBatchSize; }
        public void setEmbeddingBatchSize(int v) { this.embeddingBatchSize = v; }
    }

    public static class Retrieval {
        private int topKDense = 25;
        private int topKSparse = 25;
        private int topKFinal = 5;
        private int rrfK = 60;

        public int getTopKDense() { return topKDense; }
        public void setTopKDense(int v) { this.topKDense = v; }
        public int getTopKSparse() { return topKSparse; }
        public void setTopKSparse(int v) { this.topKSparse = v; }
        public int getTopKFinal() { return topKFinal; }
        public void setTopKFinal(int v) { this.topKFinal = v; }
        public int getRrfK() { return rrfK; }
        public void setRrfK(int v) { this.rrfK = v; }
    }

    public static class Reranker {
        private String provider = "cohere";
        private String cohereApiKey = "";
        private String model = "rerank-english-v3.0";

        public String getProvider() { return provider; }
        public void setProvider(String v) { this.provider = v; }
        public String getCohereApiKey() { return cohereApiKey; }
        public void setCohereApiKey(String v) { this.cohereApiKey = v; }
        public String getModel() { return model; }
        public void setModel(String v) { this.model = v; }
    }

    public static class Elasticsearch {
        private String hosts = "http://localhost:9200";
        private String index = "chat_chunks";

        public String getHosts() { return hosts; }
        public void setHosts(String v) { this.hosts = v; }
        public String getIndex() { return index; }
        public void setIndex(String v) { this.index = v; }
    }

    public static class Cors {
        private String allowedOrigins = "*";
        public String getAllowedOrigins() { return allowedOrigins; }
        public void setAllowedOrigins(String v) { this.allowedOrigins = v; }
    }
}
