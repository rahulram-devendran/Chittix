package com.companybrain.dto;

import java.time.Instant;
import java.util.UUID;

/**
 * Standardized API error payload containing safe, user-facing error details
 * and a unique correlation ID for server-side diagnosis without leaking internals or credentials.
 */
public record ErrorResponse(
        int status,
        String error,
        String message,
        String errorCode,
        String correlationId,
        Instant timestamp
) {
    public ErrorResponse(int status, String error, String message) {
        this(status, error, message, "ERR_" + status, UUID.randomUUID().toString(), Instant.now());
    }

    public ErrorResponse(int status, String error, String message, String errorCode, String correlationId) {
        this(status, error, message, errorCode, correlationId, Instant.now());
    }
}
