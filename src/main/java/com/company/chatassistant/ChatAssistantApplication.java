package com.company.chatassistant;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableAsync;

/**
 * Entry point for the chat-assistant service.
 *
 * Runs both the online chat API and the ingestion workers in a single JVM by default.
 * For TB-scale, split into two deployables sharing the same JAR:
 *   API replicas         : java -jar chat-assistant.jar --app.mode=api
 *   Ingestion workers    : java -jar chat-assistant.jar --app.mode=ingest
 * The wiring already tolerates both — beans are lazy on unused paths.
 */
@SpringBootApplication
@EnableKafka
@EnableAsync
public class ChatAssistantApplication {
    public static void main(String[] args) {
        SpringApplication.run(ChatAssistantApplication.class, args);
    }
}
