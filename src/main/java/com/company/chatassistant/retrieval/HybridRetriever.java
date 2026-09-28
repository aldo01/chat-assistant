package com.company.chatassistant.retrieval;

import com.company.chatassistant.config.AppProperties;
import com.company.chatassistant.model.RetrievedChunk;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Hybrid retrieval = vector (dense semantic) + BM25 (sparse keyword),
 * fused with Reciprocal Rank Fusion (RRF).
 *
 * RRF: for each candidate, score = Σ over rankers of  1 / (rrfK + rank).
 *   - No need to normalize scores across rankers (which is fiddly).
 *   - Provably robust; used in production at many search shops.
 *
 * Runs both searches in parallel — latency = max of the two, not sum.
 */
@Component
public class HybridRetriever {

    private final VectorStoreService vectorStore;
    private final BM25Store bm25;
    private final AppProperties props;

    public HybridRetriever(VectorStoreService vectorStore, BM25Store bm25, AppProperties props) {
        this.vectorStore = vectorStore;
        this.bm25 = bm25;
        this.props = props;
    }

    public List<RetrievedChunk> retrieve(String query) {
        int kd = props.getRetrieval().getTopKDense();
        int ks = props.getRetrieval().getTopKSparse();
        int rrfK = props.getRetrieval().getRrfK();

        CompletableFuture<List<RetrievedChunk>> denseF =
                CompletableFuture.supplyAsync(() -> vectorStore.search(query, kd));
        CompletableFuture<List<RetrievedChunk>> sparseF =
                CompletableFuture.supplyAsync(() -> bm25.search(query, ks));

        List<RetrievedChunk> dense = denseF.join();
        List<RetrievedChunk> sparse = sparseF.join();

        return fuseRRF(List.of(dense, sparse), rrfK);
    }

    static List<RetrievedChunk> fuseRRF(List<List<RetrievedChunk>> lists, int k) {
        Map<String, Double> scores = new HashMap<>();
        Map<String, RetrievedChunk> byId = new HashMap<>();
        for (List<RetrievedChunk> list : lists) {
            for (int rank = 0; rank < list.size(); rank++) {
                RetrievedChunk rc = list.get(rank);
                String id = rc.chunk().id();
                scores.merge(id, 1.0 / (k + rank + 1), Double::sum);
                byId.putIfAbsent(id, rc);
            }
        }
        List<Map.Entry<String, Double>> sorted = new ArrayList<>(scores.entrySet());
        sorted.sort((a, b) -> Double.compare(b.getValue(), a.getValue()));
        List<RetrievedChunk> out = new ArrayList<>();
        for (Map.Entry<String, Double> e : sorted) {
            RetrievedChunk orig = byId.get(e.getKey());
            out.add(new RetrievedChunk(orig.chunk(), e.getValue(), "fused"));
        }
        return out;
    }
}
