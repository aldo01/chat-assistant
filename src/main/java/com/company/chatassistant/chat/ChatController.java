package com.company.chatassistant.chat;

import com.company.chatassistant.model.ChatRequest;
import jakarta.validation.Valid;
import org.springframework.http.MediaType;
import org.springframework.http.codec.ServerSentEvent;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;

import java.time.Duration;

/**
 * HTTP entry point for the widget.
 *
 *   POST /chat  (SSE)
 *       body: { sessionId, question, history: [ {role, content} ] }
 *       stream:
 *         event: token     data: "hello"
 *         event: token     data: " world"
 *         ...
 *         event: citations data: { sessionId, answer, citations[], confidence }
 *
 * Also provides a non-streaming /chat/sync fallback for legacy clients / tests.
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    private final ChatService chat;

    public ChatController(ChatService chat) {
        this.chat = chat;
    }

    @PostMapping(produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public Flux<ServerSentEvent<Object>> stream(@Valid @RequestBody ChatRequest req) {
        Flux<ServerSentEvent<Object>> body = chat.streamAnswer(req)
                .map(ev -> switch (ev) {
                    case ChatService.ChatEvent.Token t ->
                            ServerSentEvent.<Object>builder(t.text()).event("token").build();
                    case ChatService.ChatEvent.Done d ->
                            ServerSentEvent.<Object>builder(d.response()).event("citations").build();
                });
        // Keep-alive pings so proxies don't kill the stream
        Flux<ServerSentEvent<Object>> keepAlive = Flux.interval(Duration.ofSeconds(15))
                .map(i -> ServerSentEvent.<Object>builder().comment("keepalive").build());
        return body.publish(shared ->
                Flux.merge(shared, keepAlive.takeUntilOther(shared.ignoreElements())));
    }

    @GetMapping("/health")
    public String health() {
        return "ok";
    }
}
