package com.companybrain.dto.source;

import java.util.Map;

/**
 * Common representation of a discoverable item from external sources
 * (GitLab project/repo, Bitbucket repository, Confluence space).
 */
public record SourceItemDto(
        String id,
        String name,
        String fullName,
        String description,
        String webUrl,
        String defaultBranch,
        String type,
        boolean isPrivate,
        Map<String, String> metadata
) {}
