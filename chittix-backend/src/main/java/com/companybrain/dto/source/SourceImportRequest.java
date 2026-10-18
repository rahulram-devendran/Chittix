package com.companybrain.dto.source;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;

/**
 * Request payload to enqueue an import job for selected items from a source provider.
 */
public record SourceImportRequest(
        String provider,

        @NotEmpty(message = "Items list must not be empty")
        List<String> items
) {}
