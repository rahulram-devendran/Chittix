package com.companybrain.dto.github;

import java.util.List;

public record GitHubJobStatusResponse(
        String jobId,
        String status, // QUEUED, RUNNING, COMPLETED, FAILED
        int repositoriesTotal,
        int repositoriesProcessed,
        int commitsImported,
        int pullRequestsImported,
        int chunksCreated,
        String currentRepository,
        String currentPhase,
        long startTimeMillis,
        Long endTimeMillis,
        List<String> logs,
        List<String> errors,
        List<String> importedRepositories
) {}
