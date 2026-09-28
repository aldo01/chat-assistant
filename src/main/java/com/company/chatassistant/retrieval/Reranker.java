package com.company.chatassistant.retrieval;

import com.company.chatassistant.config.AppProperties;
import com.company.chatassistant.model.RetrievedChunk;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import okhttp3.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Cross-encoder reranker.
 *
 * Given the fused candidates from HybridRetriever, calls a reranker model
 * that reads (query, chunk) pairs and produces a genuine relevance score.
 * This is the single biggest quality boost you can add to a RAG system.
 *
 * Providers:
 *   - "cohere"  : Cohere Rerank v3 (fast, cheap, requires COHERE_API_KEY)
 *   - "none"    : passthrough (use the RRF-fused order as-is)
 *
 * Swap in bge-reranker-base via a small local Python sidecar if you want fully offline.
 */
@Component
public class Reranker {

    private static final Logger log = LoggerFactory.getLogger(Reranker.class);
    private static final MediaType JSON = MediaType.parse("application/json");
    private static final ObjectMapper JSON_MAPPER = new ObjectMapper();

    private final AppProperties props;
    private final OkHttpClient http;

    public Reranker(AppProperties props, OkHttpClient http) {
        this.props = props;
        this.http = http;
    }

    public List<RetrievedChunk> rerank(String query, List<RetrievedChunk> candidates, int topN) {
        if (candidates.isEmpty()) return candidates;
        String provider = props.getReranker().getProvider();
        if ("none".equalsIgnoreCase(provider) || props.getReranker().getCohereApiKey().isBlank()) {
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
        try {
            return rerankCohere(query, candidates, topN);
        } catch (Exception e) {
            log.warn("Rerank failed, falling back to fused order: {}", e.getMessage());
            return candidates.subList(0, Math.min(topN, candidates.size()));
        }
    }

    private List<RetrievedChunk> rerankCohere(String query, List<RetrievedChunk> cands, int topN) throws IOException {
        List<String> docs = new ArrayList<>(cands.size());
        for (RetrievedChunk rc : cands) docs.add(rc.chunk().embeddableText());

        var payload = JSON_MAPPER.createObjectNode();
        payload.put("model", props.getReranker().getModel());
        payload.put("query", query);
        payload.put("top_n", Math.min(topN, cands.size()));
        payload.set("documents", JSON_MAPPER.valueToTree(docs));

        Request req = new Request.Builder()
                .url("https://api.cohere.com/v2/rerank")
                .addHeader("Authorization", "Bearer " + props.getReranker().getCohereApiKey())
                .post(RequestBody.create(JSON_MAPPER.writeValueAsBytes(payload), JSON))
                .build();

        try (Response resp = http.newCall(req).execute()) {
            if (!resp.isSuccessful() || resp.body() == null) {
                throw new IOException("Cohere rerank " + resp.code());
            }
            JsonNode body = JSON_MAPPER.readTree(resp.body().byteStream());
            JsonNode results = body.get("results");
            List<RetrievedChunk> out = new ArrayList<>();
            for (JsonNode r : results) {
                int idx = r.get("index").asInt();
                double score = r.get("relevance_score").asDouble();
                RetrievedChunk src = cands.get(idx);
                out.add(new RetrievedChunk(src.chunk(), score, "reranked"));
            }
            return out;
        }
    }
}
