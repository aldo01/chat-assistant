package com.company.chatassistant.embedding;

import com.company.chatassistant.model.Chunk;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Wraps Spring AI's EmbeddingModel with:
 *   - batching (single API call for many chunks)
 *   - text-selection (uses chunk.embeddableText() which includes heading context)
 *
 * Batching is critical for TB-scale ingest. Each `embed(batch)` call becomes one HTTP round-trip
 * to the embedding provider, so a batch of 64 vs one-at-a-time is 64× fewer round-trips.
 * Real bottleneck at scale is provider rate limits — throttle via a bulkhead if needed.
 */
@Component
public class EmbeddingService {

    private final EmbeddingModel embeddingModel;

    public EmbeddingService(EmbeddingModel embeddingModel) {
        this.embeddingModel = embeddingModel;
    }

    /** Embed a batch of chunks. Returns vectors in the same order as input. */
    public List<float[]> embedChunks(List<Chunk> chunks) {
        List<String> texts = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) texts.add(c.embeddableText());
        return embed(texts);
    }

    /** Embed one or more raw strings. */
    public List<float[]> embed(List<String> texts) {
        if (texts.isEmpty()) return List.of();
        var response = embeddingModel.embedForResponse(texts);
        List<float[]> out = new ArrayList<>(texts.size());
        response.getResults().forEach(r -> out.add(toFloatArray(r.getOutput())));
        return out;
    }

    public float[] embedOne(String text) {
        return embed(List.of(text)).get(0);
    }

    private static float[] toFloatArray(float[] embedding) {
        // Spring AI already gives float[]; kept for provider flexibility.
        return embedding;
    }
}
