package com.companybrain.exception;

import com.companybrain.dto.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Global exception handler providing standardized, safe API error responses
 * with correlation IDs, sanitized messages, and protected diagnostic server logging.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private static final Pattern SECRET_PATTERN = Pattern.compile(
            "(ghp_[a-zA-Z0-9]+|gho_[a-zA-Z0-9]+|github_pat_[a-zA-Z0-9_]+|glpat-[a-zA-Z0-9_-]+|sk-[a-zA-Z0-9]+|Bearer\\s+[a-zA-Z0-9._-]+)",
            Pattern.CASE_INSENSITIVE
    );

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleValidation(MethodArgumentNotValidException ex) {
        String correlationId = UUID.randomUUID().toString();
        String message = ex.getBindingResult().getFieldErrors().stream()
                .map(e -> e.getField() + ": " + e.getDefaultMessage())
                .collect(Collectors.joining("; "));
        String safeMessage = message.isEmpty() ? "Validation failed" : sanitize(message);
        log.warn("[{}] Validation error: {}", correlationId, safeMessage);
        return build(HttpStatus.BAD_REQUEST, safeMessage, "VALIDATION_FAILED", correlationId);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleUnreadable(HttpMessageNotReadableException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.warn("[{}] Unreadable request body: {}", correlationId, ex.getMessage());
        return build(HttpStatus.BAD_REQUEST,
                "Request body is missing or contains invalid JSON payload.",
                "MALFORMED_JSON", correlationId);
    }

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ErrorResponse> handleNotFound(NotFoundException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.info("[{}] Resource not found: {}", correlationId, ex.getMessage());
        return build(HttpStatus.NOT_FOUND, sanitize(ex.getMessage()), "NOT_FOUND", correlationId);
    }

    @ExceptionHandler(GraphUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleGraph(GraphUnavailableException ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Knowledge graph database unavailable: {}", correlationId, ex.getMessage());
        return build(HttpStatus.SERVICE_UNAVAILABLE,
                "Knowledge graph service (FalkorDB) is currently unavailable. Please verify the database container is running and retry.",
                "GRAPH_SERVICE_UNAVAILABLE", correlationId);
    }

    @ExceptionHandler(GitHubApiException.class)
    public ResponseEntity<ErrorResponse> handleGitHubApi(GitHubApiException ex) {
        String correlationId = UUID.randomUUID().toString();
        int rawCode = ex.getStatusCode();
        HttpStatus status = HttpStatus.resolve(rawCode);
        if (status == null) {
            status = HttpStatus.BAD_GATEWAY;
        }

        String errorCode;
        String userMessage;

        if (status == HttpStatus.UNAUTHORIZED) {
            errorCode = "GITHUB_AUTH_FAILED";
            userMessage = "GitHub authentication failed. Your access token may be invalid, revoked, or expired.";
        } else if (status == HttpStatus.FORBIDDEN) {
            errorCode = "GITHUB_RATE_LIMITED";
            userMessage = "GitHub API rate limit exceeded or access forbidden. Please check your token permissions or retry shortly.";
        } else if (status == HttpStatus.NOT_FOUND) {
            errorCode = "GITHUB_RESOURCE_NOT_FOUND";
            userMessage = "Requested GitHub repository or resource was not found or is private.";
        } else if (status == HttpStatus.GATEWAY_TIMEOUT || status.value() == 504) {
            errorCode = "GITHUB_TIMEOUT";
            userMessage = "GitHub API request timed out. Please check network connectivity and retry.";
        } else if (status == HttpStatus.BAD_GATEWAY || status.value() == 502) {
            errorCode = "GITHUB_UPSTREAM_UNAVAILABLE";
            userMessage = "GitHub API service is temporarily unreachable. Please check network connectivity and retry.";
        } else {
            errorCode = "GITHUB_API_ERROR";
            userMessage = sanitize(ex.getMessage());
        }

        log.warn("[{}] GitHub API error [status={}, code={}]: {}", correlationId, rawCode, errorCode, sanitize(ex.getMessage()));
        return build(status, userMessage, errorCode, correlationId);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatus(ResponseStatusException ex) {
        String correlationId = UUID.randomUUID().toString();
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        if (status == null) {
            status = HttpStatus.BAD_REQUEST;
        }
        String errorCode = switch (status) {
            case FORBIDDEN -> "FORBIDDEN_ACCESS";
            case UNAUTHORIZED -> "AUTHENTICATION_REQUIRED";
            case NOT_FOUND -> "RESOURCE_NOT_FOUND";
            case BAD_REQUEST -> "BAD_REQUEST";
            default -> "HTTP_" + status.value();
        };

        String rawReason = ex.getReason() != null ? ex.getReason() : ex.getMessage();
        String safeReason = sanitize(rawReason);
        log.warn("[{}] ResponseStatusException [status={}]: {}", correlationId, status.value(), safeReason);
        return build(status, safeReason, errorCode, correlationId);
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgument(IllegalArgumentException ex) {
        String correlationId = UUID.randomUUID().toString();
        String safeMessage = sanitize(ex.getMessage());
        log.warn("[{}] IllegalArgumentException: {}", correlationId, safeMessage);
        return build(HttpStatus.BAD_REQUEST, safeMessage, "INVALID_ARGUMENT", correlationId);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(Exception ex) {
        String correlationId = UUID.randomUUID().toString();
        log.error("[{}] Unhandled unexpected error in server execution", correlationId, ex);

        return build(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected internal error occurred while processing your request. Please retry. (Correlation ID: " + correlationId + ")",
                "INTERNAL_SERVER_ERROR", correlationId);
    }

    private ResponseEntity<ErrorResponse> build(HttpStatus status, String message, String errorCode, String correlationId) {
        return ResponseEntity.status(status)
                .body(new ErrorResponse(status.value(), status.getReasonPhrase(), message, errorCode, correlationId));
    }

    private static String sanitize(String input) {
        if (input == null) return "";
        return SECRET_PATTERN.matcher(input).replaceAll("[REDACTED]");
    }
}
