package com.company.chatassistant.provider;

/** Thrown when a model call is attempted before a provider + API key is configured. */
public class ProviderNotConfiguredException extends RuntimeException {
    public ProviderNotConfiguredException(String message) {
        super(message);
    }
}
