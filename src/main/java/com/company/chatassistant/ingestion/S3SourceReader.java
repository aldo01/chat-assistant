package com.company.chatassistant.ingestion;

import com.company.chatassistant.model.RawDocument;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.S3Object;

import jakarta.annotation.Nullable;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Streams a bucket/prefix of documents from S3 and hands them to a processor.
 *
 * At TB scale, don't try to list-then-fetch synchronously — that pattern degrades
 * badly. Instead, use S3 inventory reports or SNS/SQS event notifications to feed
 * Kafka as new objects land, and use this reader for backfills.
 *
 * S3Client bean is autowired if the app is configured with AWS creds; if not,
 * this component silently no-ops so local dev works without AWS.
 */
@Component
public class S3SourceReader {

    private static final Logger log = LoggerFactory.getLogger(S3SourceReader.class);

    @Autowired(required = false) @Nullable
    private S3Client s3;

    @Value("${app.s3.bucket:}") private String bucket;
    @Value("${app.s3.prefix:}") private String prefix;

    public void streamBucket(Consumer<RawDocument> consumer) {
        if (s3 == null || bucket.isBlank()) {
            log.info("S3 not configured — skipping S3 ingest");
            return;
        }
        var req = ListObjectsV2Request.builder().bucket(bucket).prefix(prefix).maxKeys(1000).build();
        String continuation = null;
        int count = 0;
        do {
            var effective = continuation == null
                    ? req
                    : req.toBuilder().continuationToken(continuation).build();
            var resp = s3.listObjectsV2(effective);
            for (S3Object obj : resp.contents()) {
                try {
                    consumer.accept(fetchOne(obj));
                    count++;
                } catch (Exception e) {
                    log.warn("Skipping s3://{}/{}: {}", bucket, obj.key(), e.getMessage());
                }
            }
            continuation = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (continuation != null);
        log.info("S3 ingest complete: {} objects", count);
    }

    private RawDocument fetchOne(S3Object obj) throws Exception {
        var request = GetObjectRequest.builder().bucket(bucket).key(obj.key()).build();
        try (ResponseInputStream<GetObjectResponse> in = s3.getObject(request);
             BufferedReader reader = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String content = reader.lines().reduce("", (a, b) -> a + "\n" + b);
            Map<String, Object> meta = new HashMap<>();
            meta.put("s3_bucket", bucket);
            meta.put("s3_key", obj.key());
            meta.put("size", obj.size());
            return new RawDocument(
                    UUID.randomUUID().toString(),
                    "s3://" + bucket + "/" + obj.key(),
                    "s3",
                    obj.key(),
                    content.strip(),
                    Instant.now(),
                    meta
            );
        }
    }
}
