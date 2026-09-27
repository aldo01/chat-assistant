package com.company.chatassistant.model;

import java.util.Map;

/**
 * A retrieved chunk with its relevance score. Emitted by the retriever,
 * consumed by the reranker and prompt builder.
 */
public record RetrievedChunk(
        Chunk chunk,
        double score,
        String retrievalSource   // "vector" | "bm25" | "fused" | "reranked"
) {
    public Map<String, Object> asPromptRef(int index) {
        return Map.of(
                "index", index,
                "url", chunk.sourceUrl(),
                "context", chunk.context() == null ? "" : chunk.context(),
                "text", chunk.text(),
                "score", score
        );
    }
}
