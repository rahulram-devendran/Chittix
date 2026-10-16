package com.companybrain.dto;

/**
 * Result of a document text ingestion operation.
 */
public record DocumentIngestionResult(
        String sourceId,
        String documentTitle,
        int chunkCount,
        String status,
        String message) {

    public static DocumentIngestionResult success(String sourceId, String documentTitle, int chunkCount) {
        return new DocumentIngestionResult(
                sourceId,
                documentTitle,
                chunkCount,
                "INGESTED",
                "Successfully ingested " + chunkCount + " chunk(s) for document '" + documentTitle + "' (" + sourceId + ")."
        );
    }

    public static DocumentIngestionResult skippedEmpty(String sourceId, String documentTitle) {
        return new DocumentIngestionResult(
                sourceId,
                documentTitle,
                0,
                "SKIPPED_EMPTY",
                "Document '" + documentTitle + "' (" + sourceId + ") content was blank; no chunks created."
        );
    }
}
