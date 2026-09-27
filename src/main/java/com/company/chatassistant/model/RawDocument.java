package com.company.chatassistant.model;

import java.time.Instant;
import java.util.Map;

/**
 * One raw document ingested from a source (a page, a PDF, an FAQ entry).
 * Emitted onto Kafka `docs.raw`; consumed by ingestion workers.
 */
public record RawDocument(
        String id,
        String sourceUrl,
        String sourceType,   // web | pdf | faq | wiki | s3
        String title,
        String content,      // cleaned Markdown
        Instant fetchedAt,
        Map<String, Object> metadata
) {}
