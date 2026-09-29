package com.company.chatassistant.ingestion;

import com.company.chatassistant.model.RawDocument;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Consumes RawDocument JSON messages from Kafka topic `docs.raw` in batches
 * and pushes them through the ingestion pipeline.
 *
 * Scaling: increase Kafka partitions and add replicas of this service
 * (spring.kafka.consumer.group-id ensures partitions rebalance across replicas).
 *
 * At TB scale, the typical bottleneck is the embedding provider. Watch the
 * consumer lag; if it grows, add embedding batch size, add worker replicas,
 * or move to a self-hosted BGE model on GPU nodes.
 */
@Component
public class KafkaIngestionListener {

    private static final Logger log = LoggerFactory.getLogger(KafkaIngestionListener.class);

    private final DocumentProcessor processor;
    private final ObjectMapper mapper;

    public KafkaIngestionListener(DocumentProcessor processor, ObjectMapper mapper) {
        this.processor = processor;
        this.mapper = mapper;
    }

    @KafkaListener(
            topics = "${app.ingest.kafka-topic-raw}",
            batch = "true",
            containerFactory = "kafkaListenerContainerFactory"
    )
    public void onBatch(@Payload List<String> messages) {
        List<RawDocument> docs = new ArrayList<>(messages.size());
        for (String msg : messages) {
            try {
                docs.add(mapper.readValue(msg, RawDocument.class));
            } catch (Exception e) {
                log.error("Bad RawDocument JSON, skipping: {}", e.getMessage());
            }
        }
        if (!docs.isEmpty()) processor.processBatch(docs);
    }
}
