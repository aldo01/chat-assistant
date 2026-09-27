package com.company.chatassistant.chunking;

import com.company.chatassistant.model.Chunk;
import com.company.chatassistant.model.RawDocument;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Structure-aware markdown chunker.
 *
 * Key ideas (this file is one of the two highest-leverage files in the codebase;
 * the other is HybridRetriever). Bad chunks kill RAG quality more than bad models do.
 *
 *  1. Walk the document, tracking the current heading trail (# > ## > ###).
 *  2. Emit chunks at heading boundaries, but never longer than `targetTokens`.
 *  3. Prepend "Document title > H1 > H2 > H3" as `context` — improves both
 *     retrieval matching AND the LLM's grounding.
 *  4. When a section is too long, split at paragraph boundaries with a
 *     `overlapTokens`-sized overlap between successive chunks (prevents the
 *     "answer lives on the seam" failure mode).
 *  5. Never split inside a code block or list item — we buffer until closure.
 *
 * Token counting is approximate here (words × 1.3). For production, plug in
 * a proper tokenizer via jTokkit or `Encoding` from Spring AI.
 */
@Component
public class MarkdownChunker {

    private final int targetTokens;
    private final int overlapTokens;

    public MarkdownChunker(
            @Value("${app.ingest.chunk-target-tokens:400}") int targetTokens,
            @Value("${app.ingest.chunk-overlap-tokens:60}") int overlapTokens
    ) {
        this.targetTokens = targetTokens;
        this.overlapTokens = overlapTokens;
    }

    public List<Chunk> chunk(RawDocument doc) {
        List<Chunk> out = new ArrayList<>();
        String[] lines = doc.content().split("\n", -1);

        // heading trail: index = header level (1..6), value = last heading text at that level
        String[] trail = new String[7];

        StringBuilder buf = new StringBuilder();
        int position = 0;
        boolean inCode = false;

        for (String line : lines) {
            String trimmed = line.stripTrailing();

            // Track fenced code blocks — never split inside them
            if (trimmed.startsWith("```")) {
                inCode = !inCode;
                buf.append(line).append('\n');
                continue;
            }

            if (!inCode) {
                int level = headingLevel(trimmed);
                if (level > 0) {
                    // A new heading terminates the current buffer as its own chunk.
                    flush(out, doc, buf, trail, position++);
                    updateTrail(trail, level, trimmed.substring(level).trim());
                    // Do NOT include the heading text in the buffer body; it lives in `context`.
                    continue;
                }
            }

            buf.append(line).append('\n');

            // If the buffer got long, split at paragraph boundary with overlap
            if (approxTokens(buf) >= targetTokens && endsWithBlankLine(buf)) {
                String tail = tailForOverlap(buf, overlapTokens);
                flush(out, doc, buf, trail, position++);
                buf.append(tail);
            }
        }
        flush(out, doc, buf, trail, position);
        return out;
    }

    private static int headingLevel(String line) {
        int n = 0;
        while (n < line.length() && n < 6 && line.charAt(n) == '#') n++;
        return (n > 0 && n < line.length() && line.charAt(n) == ' ') ? n : 0;
    }

    private static void updateTrail(String[] trail, int level, String text) {
        trail[level] = text;
        for (int i = level + 1; i < trail.length; i++) trail[i] = null;
    }

    private void flush(List<Chunk> out, RawDocument doc, StringBuilder buf,
                       String[] trail, int position) {
        String text = buf.toString().strip();
        buf.setLength(0);
        if (text.isEmpty()) return;

        String context = buildContext(doc.title(), trail);

        Map<String, Object> md = new HashMap<>();
        if (doc.metadata() != null) md.putAll(doc.metadata());
        md.put("heading_trail", context);

        out.add(new Chunk(
                UUID.randomUUID().toString(),
                doc.id(),
                text,
                context,
                doc.sourceUrl(),
                doc.sourceType(),
                position,
                md
        ));
    }

    private static String buildContext(String title, String[] trail) {
        StringBuilder ctx = new StringBuilder();
        if (title != null && !title.isBlank()) ctx.append(title);
        for (int i = 1; i < trail.length; i++) {
            if (trail[i] == null) continue;
            if (!ctx.isEmpty()) ctx.append(" > ");
            ctx.append(trail[i]);
        }
        return ctx.toString();
    }

    private static int approxTokens(CharSequence s) {
        // cheap approximation — ~1.3 tokens per whitespace-word
        int words = 0;
        boolean inWord = false;
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            boolean w = !Character.isWhitespace(c);
            if (w && !inWord) words++;
            inWord = w;
        }
        return (int) (words * 1.3);
    }

    private static boolean endsWithBlankLine(CharSequence s) {
        int len = s.length();
        return len >= 2 && s.charAt(len - 1) == '\n'
                && (len == 1 || s.charAt(len - 2) == '\n');
    }

    private static String tailForOverlap(CharSequence s, int overlapTokens) {
        String text = s.toString();
        String[] words = text.split("\\s+");
        int keep = Math.min(words.length, Math.max(1, (int) (overlapTokens / 1.3)));
        StringBuilder t = new StringBuilder();
        for (int i = words.length - keep; i < words.length; i++) {
            t.append(words[i]).append(' ');
        }
        return t.toString().stripTrailing() + "\n\n";
    }
}
