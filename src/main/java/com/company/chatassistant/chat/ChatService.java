package com.company.chatassistant.chat;

import com.company.chatassistant.config.AppProperties;
import com.company.chatassistant.model.ChatRequest;
import com.company.chatassistant.model.ChatResponse;
import com.company.chatassistant.model.RetrievedChunk;
import com.company.chatassistant.provider.ProviderConfigStore;
import com.company.chatassistant.retrieval.HybridRetriever;
import com.company.chatassistant.retrieval.Reranker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;

import java.util.ArrayList;
import java.util.List;

/**
 * The heart of the online path.
 *
 * pipeline per request:
 *   1. Rewrite query (using history).
 *   2. Retrieve hybrid (vector + BM25 → RRF).
 *   3. Rerank top-k.
 *   4. Build prompt.
 *   5. Stream LLM tokens back.
 *   6. Emit final citations envelope.
 *
 * Streaming uses Spring AI's reactive stream API; the controller converts to SSE.
 */
@Service
public class ChatService {

    private static final Logger log = LoggerFactory.getLogger(ChatService.class);

    private final QueryRewriter rewriter;
    private final HybridRetriever retriever;
    private final Reranker reranker;
    private final PromptBuilder promptBuilder;
    private final ChatModel chatModel;
    private final AppProperties props;
    private final ProviderConfigStore providerConfig;

    public ChatService(QueryRewriter rewriter,
                       HybridRetriever retriever,
                       Reranker reranker,
                       PromptBuilder promptBuilder,
                       ChatModel chatModel,
                       AppProperties props,
                       ProviderConfigStore providerConfig) {
        this.rewriter = rewriter;
        this.retriever = retriever;
        this.reranker = reranker;
        this.promptBuilder = promptBuilder;
        this.chatModel = chatModel;
        this.props = props;
        this.providerConfig = providerConfig;
    }

    /**
     * Returns a Flux that emits token-events during generation, then a final
     * `citations` event. The controller adapts this to SSE.
     */
    public Flux<ChatEvent> streamAnswer(ChatRequest req) {
        String rewritten;
        try {
            rewritten = rewriter.rewrite(req);
        } catch (Exception e) {
            log.warn("Rewrite failed, using raw question", e);
            rewritten = req.question();
        }
        log.info("q='{}' rewritten='{}'", req.question(), rewritten);

        List<RetrievedChunk> fused = retriever.retrieve(rewritten);
        List<RetrievedChunk> top = reranker.rerank(rewritten, fused, props.getRetrieval().getTopKFinal());
        log.info("retrieved={} reranked={}", fused.size(), top.size());

        var messages = promptBuilder.build(req, top, providerConfig.get().getBrand());
        Prompt prompt = new Prompt(messages);

        StringBuilder full = new StringBuilder();
        return chatModel.stream(prompt)
                .map(chunk -> chunk.getResult().getOutput().getContent())
                .doOnNext(full::append)
                .map(text -> (ChatEvent) new ChatEvent.Token(text))
                .concatWith(Flux.defer(() -> Flux.just(new ChatEvent.Done(
                        buildCitationsResponse(req.sessionId(), full.toString(), top)))));
    }

    private ChatResponse buildCitationsResponse(String sessionId, String answer, List<RetrievedChunk> top) {
        List<ChatResponse.Citation> citations = new ArrayList<>();
        for (RetrievedChunk rc : top) {
            String snippet = rc.chunk().text();
            if (snippet.length() > 200) snippet = snippet.substring(0, 200) + "…";
            citations.add(new ChatResponse.Citation(
                    rc.chunk().sourceUrl(),
                    rc.chunk().context() == null ? "" : rc.chunk().context(),
                    snippet,
                    rc.score()
            ));
        }
        String confidence = citations.isEmpty() ? "low" : "high";
        return new ChatResponse(sessionId, answer, citations, confidence);
    }

    /** SSE event envelope. */
    public sealed interface ChatEvent {
        record Token(String text) implements ChatEvent {}
        record Done(ChatResponse response) implements ChatEvent {}
    }
}
