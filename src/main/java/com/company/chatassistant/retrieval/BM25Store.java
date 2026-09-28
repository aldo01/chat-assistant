package com.company.chatassistant.retrieval;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.elasticsearch._types.mapping.Property;
import co.elastic.clients.elasticsearch._types.mapping.TextProperty;
import co.elastic.clients.elasticsearch._types.mapping.KeywordProperty;
import co.elastic.clients.elasticsearch.core.BulkRequest;
import co.elastic.clients.elasticsearch.core.BulkResponse;
import co.elastic.clients.elasticsearch.core.SearchResponse;
import co.elastic.clients.elasticsearch.indices.CreateIndexRequest;
import co.elastic.clients.elasticsearch.indices.ExistsRequest;
import co.elastic.clients.elasticsearch.core.search.Hit;
import com.company.chatassistant.config.AppProperties;
import com.company.chatassistant.model.Chunk;
import com.company.chatassistant.model.RetrievedChunk;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Elasticsearch-backed BM25 store.
 * Handles both indexing (called by ingestion workers) and top-K query (called by
 * HybridRetriever on the online path).
 *
 * Index shape:
 *   { id, document_id, text, context, source_url, source_type, position, metadata }
 * `text` and `context` use standard analyzer; everything else is `keyword` for filters.
 */
@Component
public class BM25Store {

    private static final Logger log = LoggerFactory.getLogger(BM25Store.class);

    private final ElasticsearchClient es;
    private final String index;

    public BM25Store(ElasticsearchClient es, AppProperties props) {
        this.es = es;
        this.index = props.getElasticsearch().getIndex();
    }

    @PostConstruct
    void ensureIndex() {
        try {
            boolean exists = es.indices().exists(ExistsRequest.of(r -> r.index(index))).value();
            if (exists) return;
            Map<String, Property> mapping = new HashMap<>();
            mapping.put("document_id", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
            mapping.put("text",        Property.of(p -> p.text(TextProperty.of(t -> t))));
            mapping.put("context",     Property.of(p -> p.text(TextProperty.of(t -> t))));
            mapping.put("source_url",  Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));
            mapping.put("source_type", Property.of(p -> p.keyword(KeywordProperty.of(k -> k))));

            es.indices().create(CreateIndexRequest.of(r -> r
                    .index(index)
                    .mappings(m -> m.properties(mapping))));
            log.info("Created ES index {}", index);
        } catch (IOException e) {
            log.warn("Failed to ensure ES index — BM25 disabled: {}", e.getMessage());
        }
    }

    public void indexBatch(List<Chunk> chunks) {
        if (chunks.isEmpty()) return;
        try {
            BulkRequest.Builder br = new BulkRequest.Builder();
            for (Chunk c : chunks) {
                Map<String, Object> doc = new HashMap<>();
                doc.put("document_id", c.documentId());
                doc.put("text", c.text());
                doc.put("context", c.context() == null ? "" : c.context());
                doc.put("source_url", c.sourceUrl());
                doc.put("source_type", c.sourceType());
                doc.put("position", c.position());
                if (c.metadata() != null) doc.put("metadata", c.metadata());
                br.operations(op -> op.index(idx -> idx.index(index).id(c.id()).document(doc)));
            }
            BulkResponse resp = es.bulk(br.build());
            if (resp.errors()) {
                log.warn("ES bulk had errors for {} docs", chunks.size());
            }
        } catch (IOException e) {
            log.error("BM25 index failure", e);
        }
    }

    /** Return top-K chunks by BM25 score against text + context. */
    public List<RetrievedChunk> search(String query, int topK) {
        try {
            SearchResponse<Map> resp = es.search(s -> s
                    .index(index)
                    .size(topK)
                    .query(q -> q.multiMatch(m -> m
                            .query(query)
                            .fields("text^2", "context^1.5")
                    )), Map.class);

            List<RetrievedChunk> out = new ArrayList<>();
            for (Hit<Map> h : resp.hits().hits()) {
                Map<String, Object> src = h.source();
                if (src == null) continue;
                Chunk chunk = new Chunk(
                        h.id(),
                        (String) src.getOrDefault("document_id", ""),
                        (String) src.getOrDefault("text", ""),
                        (String) src.getOrDefault("context", ""),
                        (String) src.getOrDefault("source_url", ""),
                        (String) src.getOrDefault("source_type", ""),
                        ((Number) src.getOrDefault("position", 0)).intValue(),
                        (Map<String, Object>) src.getOrDefault("metadata", Map.of())
                );
                out.add(new RetrievedChunk(chunk, h.score() == null ? 0 : h.score(), "bm25"));
            }
            return out;
        } catch (IOException e) {
            log.error("BM25 search failure", e);
            return List.of();
        }
    }
}
