package com.companybrain.dto.github;

import jakarta.validation.constraints.NotBlank;

/**
 * Request payload for configuring in-memory GitHub access credentials.
 * The token is never logged, exposed to frontend, or written to disk.
 */
public record GitHubConfigRequest(
        @NotBlank(message = "GitHub access token must not be blank")
        String token
) {
}
