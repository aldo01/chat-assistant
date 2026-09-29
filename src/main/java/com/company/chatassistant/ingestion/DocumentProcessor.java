package com.company.chatassistant.ingestion;

import com.company.chatassistant.chunking.MarkdownChunker;
import com.company.chatassistant.model.Chunk;
import com.company.chatassistant.model.RawDocument;
import com.company.chatassistant.retrieval.BM25Store;
import com.company.chatassistant.retrieval.VectorStoreService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * The core ingestion pipeline: RawDocument → chunks → (embed + upsert) → (index BM25).
 *
 * Called from:
 *   - KafkaIngestionListener (streaming / TB-scale continuous)
 *   - IngestionController (manual push / smoke tests)
 *
 * Idempotency: chunk IDs are stable UUIDs derived from documentId + position via MarkdownChunker.
 * Re-ingesting the same document will overwrite, not duplicate, thanks to Qdrant / ES upsert-by-id.
 */
@Service
public class DocumentProcessor {

    private static final Logger log = LoggerFactory.getLogger(DocumentProcessor.class);

    private final MarkdownChunker chunker;
    private final VectorStoreService vectorStore;
    private final BM25Store bm25;

    public DocumentProcessor(MarkdownChunker chunker, VectorStoreService vectorStore, BM25Store bm25) {
        this.chunker = chunker;
        this.vectorStore = vectorStore;
        this.bm25 = bm25;
    }

    public int process(RawDocument doc) {
        List<Chunk> chunks = chunker.chunk(doc);
        if (chunks.isEmpty()) {
            log.debug("Empty document ignored: {}", doc.sourceUrl());
            return 0;
        }
        vectorStore.upsert(chunks);   // Spring AI VectorStore batches and embeds internally
        bm25.indexBatch(chunks);
        log.info("Indexed {} chunks from {}", chunks.size(), doc.sourceUrl());
        return chunks.size();
    }

    public int processBatch(List<RawDocument> docs) {
        int total = 0;
        List<Chunk> allChunks = new ArrayList<>();
        for (RawDocument d : docs) allChunks.addAll(chunker.chunk(d));
        if (allChunks.isEmpty()) return 0;

        vectorStore.upsert(allChunks);
        bm25.indexBatch(allChunks);
        total = allChunks.size();
        log.info("Batch indexed {} chunks from {} docs", total, docs.size());
        return total;
    }
}
