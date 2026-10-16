package com.companybrain.dto;

/**
 * Indicates the availability of underlying evidence for answer generation.
 */
public enum EvidenceStatus {
    /** Both structural graph facts and document excerpts were retrieved. */
    FULL_EVIDENCE,
    /** Only structural graph facts were found; no document passages retrieved. */
    GRAPH_ONLY,
    /** Only document passages were retrieved; no graph paths matched. */
    DOCUMENTS_ONLY,
    /** Neither graph facts nor document passages were found. */
    INSUFFICIENT
}
