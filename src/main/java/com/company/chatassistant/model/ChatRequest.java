package com.company.chatassistant.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Payload for POST /chat.
 */
public record ChatRequest(
        @NotBlank String sessionId,
        @NotBlank @Size(max = 2000) String question,
        List<ChatTurn> history   // may be null / empty
) {
    public record ChatTurn(String role, String content) {}
}
