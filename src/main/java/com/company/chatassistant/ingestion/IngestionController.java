package com.company.chatassistant.ingestion;

import com.company.chatassistant.model.RawDocument;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;
import java.util.Map;

/**
 * Manual ingestion endpoints — useful for smoke tests, small corpora, and admin flows.
 * For real TB-scale ingest, produce onto Kafka topic `docs.raw` instead.
 *
 *   POST /admin/ingest/url         { "url": "https://acme.com/help/billing" }
 *   POST /admin/ingest/pdf         multipart file upload
 *   POST /admin/ingest/text        RawDocument JSON body
 *   POST /admin/ingest/s3          triggers S3 bucket scan (uses configured bucket)
 *
 * PROTECT THIS WITH AUTH in production. Left open here for local dev clarity.
 */
@RestController
@RequestMapping("/admin/ingest")
public class IngestionController {

    private final DocumentProcessor processor;
    private final WebPageReader webReader;
    private final PdfReader pdfReader;
    private final S3SourceReader s3Reader;

    public IngestionController(DocumentProcessor processor,
                               WebPageReader webReader,
                               PdfReader pdfReader,
                               S3SourceReader s3Reader) {
        this.processor = processor;
        this.webReader = webReader;
        this.pdfReader = pdfReader;
        this.s3Reader = s3Reader;
    }

    @PostMapping("/url")
    public Map<String, Object> ingestUrl(@RequestBody Map<String, String> body) throws Exception {
        String url = body.get("url");
        RawDocument doc = webReader.fetch(url);
        int chunks = processor.process(doc);
        return Map.of("url", url, "chunks", chunks, "documentId", doc.id());
    }

    @PostMapping("/pdf")
    public Map<String, Object> ingestPdf(@RequestParam("file") MultipartFile file) throws Exception {
        RawDocument doc = pdfReader.fromStream(
                file.getOriginalFilename(),
                file.getOriginalFilename(),
                file.getInputStream());
        int chunks = processor.process(doc);
        return Map.of("filename", file.getOriginalFilename(), "chunks", chunks);
    }

    @PostMapping("/text")
    public Map<String, Object> ingestText(@RequestBody RawDocument doc) {
        int chunks = processor.process(doc);
        return Map.of("documentId", doc.id(), "chunks", chunks);
    }

    @PostMapping("/batch")
    public Map<String, Object> ingestBatch(@RequestBody List<RawDocument> docs) {
        int chunks = processor.processBatch(docs);
        return Map.of("documents", docs.size(), "chunks", chunks);
    }

    @PostMapping("/s3")
    public Map<String, Object> ingestS3() {
        final int[] count = { 0 };
        s3Reader.streamBucket(doc -> {
            processor.process(doc);
            count[0]++;
        });
        return Map.of("documents", count[0]);
    }
}
