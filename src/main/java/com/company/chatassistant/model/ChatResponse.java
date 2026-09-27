package com.company.chatassistant.model;

import java.util.List;

/**
 * Final envelope sent after the streaming answer.
 * The token stream is delivered as SSE `data:` frames; this record is emitted
 * as a final SSE frame with event name `citations`.
 */
public record ChatResponse(
        String sessionId,
        String answer,
        List<Citation> citations,
        String confidence
) {
    public record Citation(
            String url,
            String title,
            String snippet,
            double score
    ) {}
}
