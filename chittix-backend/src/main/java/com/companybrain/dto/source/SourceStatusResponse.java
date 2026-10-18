package com.companybrain.dto.source;

/**
 * Status response for a source integration (GitHub, GitLab, Bitbucket, Confluence).
 * Never exposes credentials, secrets, or internal architecture details.
 */
public record SourceStatusResponse(
        String provider,
        boolean configured,
        boolean connected,
        String username,
        String displayName,
        String baseUrl,
        String message,
        boolean ingestionSupported,
        String ingestionNotice
) {
    public static SourceStatusResponse unconfigured(String provider, String baseUrl, String message) {
        return new SourceStatusResponse(
                provider.toLowerCase(),
                false,
                false,
                null,
                null,
                baseUrl,
                message,
                true,
                "Metadata ingestion is active for " + provider + "."
        );
    }
}
