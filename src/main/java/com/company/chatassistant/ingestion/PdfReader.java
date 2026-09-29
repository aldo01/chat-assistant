package com.company.chatassistant.ingestion;

import com.company.chatassistant.model.RawDocument;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

import java.io.File;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Component
public class PdfReader {

    public RawDocument fromPath(Path path) throws Exception {
        try (PDDocument pdf = Loader.loadPDF(path.toFile())) {
            PDFTextStripper stripper = new PDFTextStripper();
            stripper.setSortByPosition(true);
            String text = stripper.getText(pdf);
            Map<String, Object> meta = new HashMap<>();
            meta.put("pages", pdf.getNumberOfPages());
            meta.put("filename", path.getFileName().toString());
            return new RawDocument(
                    UUID.randomUUID().toString(),
                    path.toUri().toString(),
                    "pdf",
                    path.getFileName().toString(),
                    text.strip(),
                    Instant.now(),
                    meta
            );
        }
    }

    public RawDocument fromStream(String sourceUrl, String title, InputStream in) throws Exception {
        byte[] bytes = in.readAllBytes();
        Path tmp = Files.createTempFile("ingest-", ".pdf");
        Files.write(tmp, bytes);
        try {
            RawDocument doc = fromPath(tmp);
            return new RawDocument(doc.id(), sourceUrl, "pdf",
                    title == null ? doc.title() : title,
                    doc.content(), doc.fetchedAt(), doc.metadata());
        } finally {
            Files.deleteIfExists(tmp);
        }
    }
}
