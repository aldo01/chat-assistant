package com.company.chatassistant.ingestion;

import com.company.chatassistant.model.RawDocument;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Fetches an HTML page and produces a clean RawDocument.
 *   - Strips nav / footer / script / style / cookie banners.
 *   - Uses the `<main>` / `<article>` element if present.
 *   - Converts to Markdown-ish text with heading levels preserved.
 *
 * This is a simple hand-rolled version — for hard cases (SPAs, paywalls, JS-rendered),
 * plug in Playwright or trafilatura via a sidecar. For most doc/help sites, Jsoup is enough.
 */
@Component
public class WebPageReader {

    public RawDocument fetch(String url) throws Exception {
        var doc = Jsoup.connect(url)
                .userAgent("chat-assistant/0.1 (+https://yourcompany.com)")
                .timeout(15_000)
                .get();

        // Strip boilerplate
        doc.select("script, style, noscript, nav, footer, header.site-header, .cookie-banner, .cookie-notice").remove();

        Element root = doc.selectFirst("main");
        if (root == null) root = doc.selectFirst("article");
        if (root == null) root = doc.body();

        StringBuilder md = new StringBuilder();
        walk(root, md);

        Map<String, Object> meta = new HashMap<>();
        meta.put("host", doc.location());

        return new RawDocument(
                UUID.randomUUID().toString(),
                url,
                "web",
                doc.title(),
                md.toString().strip(),
                Instant.now(),
                meta
        );
    }

    private void walk(Element el, StringBuilder md) {
        for (Element child : el.children()) {
            String tag = child.tagName();
            switch (tag) {
                case "h1" -> md.append("# ").append(child.text()).append("\n\n");
                case "h2" -> md.append("## ").append(child.text()).append("\n\n");
                case "h3" -> md.append("### ").append(child.text()).append("\n\n");
                case "h4" -> md.append("#### ").append(child.text()).append("\n\n");
                case "li" -> md.append("- ").append(child.text()).append('\n');
                case "pre", "code" -> md.append("```\n").append(child.text()).append("\n```\n\n");
                case "p" -> md.append(child.text()).append("\n\n");
                default -> {
                    if (!child.children().isEmpty()) {
                        walk(child, md);
                    } else {
                        String t = child.text().strip();
                        if (!t.isEmpty()) md.append(t).append("\n\n");
                    }
                }
            }
        }
    }
}
