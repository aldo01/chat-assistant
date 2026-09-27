package com.company.chatassistant.model;

import java.util.Map;

/**
 * A chunk of a document ready to be embedded, retrieved, and cited.
 * `text` is what we embed and what we hand to the LLM.
 * `context` (heading trail, title) is prepended to `text` before embedding
 * to improve retrieval quality — see MarkdownChunker.
 */
public record Chunk(
        String id,
        String documentId,
        String text,
        String context,           // e.g. "Acme Help > Billing > Canceling"
        String sourceUrl,
        String sourceType,
        int position,
        Map<String, Object> metadata
) {
    public String embeddableText() {
        return context == null || context.isBlank()
                ? text
                : context + "\n\n" + text;
    }
}
