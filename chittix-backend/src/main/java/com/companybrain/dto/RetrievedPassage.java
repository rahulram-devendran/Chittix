package com.companybrain.dto;

/**
 * Represents a verified text excerpt retrieved from an indexed document chunk.
 * Preserves strict provenance to the parent document with real identifiers and titles.
 *
 * @param chunkId        stable chunk identifier (e.g. "DOC-001#chunk-0")
 * @param chunkIndex      sequential index of the chunk in the parent document
 * @param text           exact text excerpt from the document
 * @param sourceId        real document source ID (e.g. "DOC-001")
 * @param documentTitle   real document title (e.g. "Payment Architecture Design")
 * @param distanceScore   cosine distance metric from FalkorDB (0.0 is exact match)
 * @param similarityScore cosine similarity metric (1.0 - distanceScore)
 */
public record RetrievedPassage(
        String chunkId,
        int chunkIndex,
        String text,
        String sourceId,
        String documentTitle,
        double distanceScore,
        double similarityScore
) {
    public static RetrievedPassage fromRetrievedChunk(RetrievedChunk chunk) {
        if (chunk == null) {
            return null;
        }
        return new RetrievedPassage(
                chunk.chunkId(),
                chunk.chunkIndex(),
                chunk.content(),
                chunk.sourceId(),
                chunk.documentTitle(),
                chunk.score(),
                chunk.similarityScore()
        );
    }
}
