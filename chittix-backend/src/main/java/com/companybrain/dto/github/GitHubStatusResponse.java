package com.companybrain.dto.github;

import java.util.List;

public record GitHubStatusResponse(
        boolean configured,
        String username,
        String name,
        String avatarUrl,
        List<String> scopes,
        int rateLimitLimit,
        int rateLimitRemaining,
        long rateLimitResetEpochSeconds,
        String message
) {
    public static GitHubStatusResponse unconfigured(String message) {
        return new GitHubStatusResponse(
                false, null, null, null, List.of(), 0, 0, 0, message
        );
    }
}
