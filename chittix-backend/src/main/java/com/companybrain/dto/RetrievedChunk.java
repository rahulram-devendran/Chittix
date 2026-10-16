package com.companybrain.dto;

import java.util.Map;

/**
 * Represents a document chunk retrieved via semantic vector search from FalkorDB.
 *
 * @param chunkId         stable chunk identifier (e.g. "DOC-001#chunk-0")
 * @param chunkIndex       sequential zero-based chunk index
 * @param content          text passage content
 * @param sourceId         identifier of the parent document (e.g. "DOC-001")
 * @param documentTitle    title of the parent document
 * @param score            native distance score from FalkorDB (cosine distance: 0.0 is identical)
 * @param similarityScore  cosine similarity metric (1.0 - score)
 * @param metadata         additional metadata attributes from the graph
 */
public record RetrievedChunk(
        String chunkId,
        int chunkIndex,
        String content,
        String sourceId,
        String documentTitle,
        double score,
        double similarityScore,
        Map<String, Object> metadata
) {
    public RetrievedChunk {
        if (metadata == null) {
            metadata = Map.of();
        }
    }
}
