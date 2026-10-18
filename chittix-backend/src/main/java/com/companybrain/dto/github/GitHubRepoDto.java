package com.companybrain.dto.github;

public record GitHubRepoDto(
        long id,
        String name,
        String fullName,
        String description,
        String htmlUrl,
        String defaultBranch,
        boolean isPrivate,
        int stargazersCount,
        int forksCount,
        int openIssuesCount,
        String updatedAt
) {}
