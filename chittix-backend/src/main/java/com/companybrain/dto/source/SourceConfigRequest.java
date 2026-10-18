package com.companybrain.dto.source;

import jakarta.validation.constraints.NotBlank;

/**
 * Payload for testing and configuring third-party source credentials (GitLab, Bitbucket, Confluence).
 * Credentials stay in backend memory only and are never exposed or logged.
 */
public record SourceConfigRequest(
        @NotBlank(message = "Source provider is required")
        String provider,

        @NotBlank(message = "Access token or credentials required")
        String token,

        String username,
        String baseUrl
) {
}
