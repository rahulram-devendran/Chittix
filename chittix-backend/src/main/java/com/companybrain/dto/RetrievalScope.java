package com.companybrain.dto;

import java.util.Collections;
import java.util.Set;

/**
 * Defines the authorized boundary for vector and document retrieval.
 * Prevents cross-project or cross-repository evidence contamination.
 */
public record RetrievalScope(
        ScopeType type,
        Set<String> authorizedSourceIds,
        Set<String> authorizedRepositories,
        String scopeDescription
) {
    public enum ScopeType {
        ARCHITECTURE,
        REPOSITORY,
        HYBRID,
        UNSCOPED
    }

    public static RetrievalScope architecture(Set<String> authorizedSourceIds) {
        return architecture(authorizedSourceIds, "Architecture");
    }

    public static RetrievalScope architecture(Set<String> authorizedSourceIds, String projectOrContext) {
        return new RetrievalScope(
                ScopeType.ARCHITECTURE,
                authorizedSourceIds == null ? Collections.emptySet() : Set.copyOf(authorizedSourceIds),
                Collections.emptySet(),
                "Architecture scope for: " + projectOrContext
        );
    }

    public static RetrievalScope repository(String repository) {
        return repository(repository == null ? Collections.emptySet() : Set.of(repository));
    }

    public static RetrievalScope repository(Set<String> repositories) {
        return new RetrievalScope(
                ScopeType.REPOSITORY,
                Collections.emptySet(),
                repositories == null ? Collections.emptySet() : Set.copyOf(repositories),
                "Repository scope for: " + String.join(", ", repositories)
        );
    }

    public static RetrievalScope hybrid(Set<String> authorizedSourceIds, Set<String> repositories, String description) {
        return new RetrievalScope(
                ScopeType.HYBRID,
                authorizedSourceIds == null ? Collections.emptySet() : Set.copyOf(authorizedSourceIds),
                repositories == null ? Collections.emptySet() : Set.copyOf(repositories),
                description
        );
    }

    public static RetrievalScope unscoped() {
        return new RetrievalScope(
                ScopeType.UNSCOPED,
                Collections.emptySet(),
                Collections.emptySet(),
                "Unscoped"
        );
    }

    /**
     * Checks if a retrieved chunk's source ID is permitted under this scope.
     */
    public boolean isAllowed(String sourceId) {
        if (sourceId == null || sourceId.isBlank()) {
            return false;
        }

        switch (type) {
            case ARCHITECTURE:
                return authorizedSourceIds.contains(sourceId);

            case REPOSITORY:
                return authorizedRepositories.stream().anyMatch(repo -> matchesSource(repo, sourceId));

            case HYBRID:
                boolean allowedArch = authorizedSourceIds.contains(sourceId)
                        || authorizedSourceIds.stream().anyMatch(authId -> sourceId.startsWith(authId) || authId.contains(sourceId));
                boolean allowedRepo = authorizedRepositories.stream().anyMatch(repo -> matchesSource(repo, sourceId));
                return allowedArch || allowedRepo;

            case UNSCOPED:
            default:
                // When scope cannot be reliably determined, unrestricted retrieval across
                // unrelated repositories/projects is disallowed to prevent evidence leakage.
                return false;
        }
    }

    private static boolean matchesSource(String repo, String sourceId) {
        if (repo == null || repo.isBlank() || sourceId == null || sourceId.isBlank()) {
            return false;
        }
        return sourceId.startsWith("github:repo:" + repo)
                || sourceId.startsWith("github:commit:" + repo)
                || sourceId.startsWith("github:pr:" + repo)
                || sourceId.startsWith("gitlab:repo:" + repo)
                || sourceId.startsWith("gitlab:commit:" + repo)
                || sourceId.startsWith("gitlab:mr:" + repo)
                || sourceId.startsWith("bitbucket:repo:" + repo)
                || sourceId.startsWith("bitbucket:commit:" + repo)
                || sourceId.startsWith("bitbucket:pr:" + repo)
                || sourceId.startsWith("confluence:space:" + repo)
                || sourceId.startsWith("confluence:page:" + repo)
                || sourceId.contains(repo);
    }
}
