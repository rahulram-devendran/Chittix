package com.companybrain.dto.github;

public record GitHubPullRequestDto(
        int number,
        String title,
        String body,
        String state,
        String authorLogin,
        String createdAt,
        String updatedAt,
        String mergedAt,
        String htmlUrl,
        String reviewSummary
) {}
