package com.companybrain.dto.source;

import java.util.List;

/**
 * Status and metrics response for an asynchronous source metadata import job.
 */
public record SourceJobStatusResponse(
        String jobId,
        String provider,
        String status, // QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_WARNINGS, FAILED
        int itemsTotal,
        int itemsProcessed,
        int commitsImported,
        int pullRequestsImported,
        int chunksCreated,
        String currentItem,
        String currentPhase,
        long startTimeMillis,
        Long endTimeMillis,
        List<String> logs,
        List<String> errors,
        List<String> importedItems
) {}
