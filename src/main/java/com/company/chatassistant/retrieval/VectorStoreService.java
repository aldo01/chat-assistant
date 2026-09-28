package com.company.chatassistant.retrieval;

import com.company.chatassistant.embedding.EmbeddingService;
import com.company.chatassistant.model.Chunk;
import com.company.chatassistant.model.RetrievedChunk;
import org.springframework.ai.document.Document;
import org.springframework.ai.vectorstore.SearchRequest;
import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Qdrant-backed vector store facade.
 * Uses Spring AI's VectorStore abstraction so swapping to Milvus / Weaviate / pgvector
 * is a config change, not a code change.
 */
@Component
public class VectorStoreService {

    private final VectorStore vectorStore;
    private final EmbeddingService embeddings;

    public VectorStoreService(VectorStore vectorStore, EmbeddingService embeddings) {
        this.vectorStore = vectorStore;
        this.embeddings = embeddings;
    }

    /** Upsert a batch of already-embedded chunks. */
    public void upsert(List<Chunk> chunks) {
        if (chunks.isEmpty()) return;
        List<Document> docs = new ArrayList<>(chunks.size());
        for (Chunk c : chunks) {
            Map<String, Object> meta = new HashMap<>();
            meta.put("document_id", c.documentId());
            meta.put("source_url", c.sourceUrl());
            meta.put("source_type", c.sourceType());
            meta.put("position", c.position());
            meta.put("context", c.context() == null ? "" : c.context());
            meta.put("text", c.text());
            if (c.metadata() != null) meta.putAll(c.metadata());

            // Spring AI's VectorStore embeds on our behalf using the configured EmbeddingModel.
            docs.add(new Document(c.id(), c.embeddableText(), meta));
        }
        vectorStore.add(docs);
    }

    /** Return top-K by cosine similarity. */
    public List<RetrievedChunk> search(String query, int topK) {
        List<Document> results = vectorStore.similaritySearch(
                SearchRequest.query(query).withTopK(topK).withSimilarityThreshold(0.0)
        );
        List<RetrievedChunk> out = new ArrayList<>(results.size());
        for (Document d : results) {
            Map<String, Object> md = d.getMetadata();
            Chunk chunk = new Chunk(
                    d.getId(),
                    (String) md.getOrDefault("document_id", ""),
                    (String) md.getOrDefault("text", d.getContent()),
                    (String) md.getOrDefault("context", ""),
                    (String) md.getOrDefault("source_url", ""),
                    (String) md.getOrDefault("source_type", ""),
                    ((Number) md.getOrDefault("position", 0)).intValue(),
                    md
            );
            double score = md.get("distance") instanceof Number n ? 1.0 - n.doubleValue() : 0.0;
            out.add(new RetrievedChunk(chunk, score, "vector"));
        }
        return out;
    }
}
