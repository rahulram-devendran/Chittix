package com.companybrain.dto.github;

public record GitHubCommitDto(
        String sha,
        String message,
        String authorName,
        String authorEmail,
        String date,
        String htmlUrl
) {}
