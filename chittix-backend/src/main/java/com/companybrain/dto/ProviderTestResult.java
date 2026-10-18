package com.companybrain.dto;

/**
 * Result of testing an AI provider connection.
 * Returns safe status feedback without exposing credentials or sensitive headers.
 */
public record ProviderTestResult(
        boolean success,
        String provider,
        String model,
        String message,
        Long latencyMs
) {}
