package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.RetrievedChunk;
import com.companybrain.exception.GraphUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for semantic vector retrieval of document chunks from FalkorDB.
 * Uses native vector search (CALL db.idx.vector.queryNodes) with local 384-dimensional embeddings.
 */
@Service
public class VectorRetrievalService {

    private static final Logger log = LoggerFactory.getLogger(VectorRetrievalService.class);
    public static final int DEFAULT_TOP_K = 5;
    public static final int MAX_ALLOWED_TOP_K = 100;

    private final GraphClient graphClient;
    private final VectorSchemaService vectorSchemaService;
    private final EmbeddingService embeddingService;
    private final VectorSchemaProperties properties;
    private final Double configuredMaxDistance;

    public VectorRetrievalService(GraphClient graphClient,
                                  VectorSchemaService vectorSchemaService,
                                  EmbeddingService embeddingService,
                                  VectorSchemaProperties properties,
                                  @Value("${falkordb.vector.max-distance:#{null}}") Double configuredMaxDistance) {
        this.graphClient = graphClient;
        this.vectorSchemaService = vectorSchemaService;
        this.embeddingService = embeddingService;
        this.properties = properties;
        this.configuredMaxDistance = configuredMaxDistance;
    }

    /**
     * Retrieves the top DEFAULT_TOP_K (5) most relevant document chunks for a question.
     */
    public List<RetrievedChunk> retrieve(String question) {
        return retrieve(question, DEFAULT_TOP_K, configuredMaxDistance);
    }

    /**
     * Retrieves the top K most relevant document chunks for a question.
     */
    public List<RetrievedChunk> retrieve(String question, int topK) {
        return retrieve(question, topK, configuredMaxDistance);
    }

    /**
     * Retrieves the top K most relevant document chunks for a question with optional max distance filter.
     * Note: In FalkorDB with cosine similarityFunction, score represents cosine distance (0.0 is identical match).
     *
     * @param question    query text (must not be blank)
     * @param topK        maximum number of chunks to retrieve (must be between 1 and 100)
     * @param maxDistance optional maximum allowable distance score (null for no cutoff)
     * @return ordered list of RetrievedChunk in ascending order of distance (highest similarity first)
     */
    public List<RetrievedChunk> retrieve(String question, int topK, Double maxDistance) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0, got: " + topK);
        }
        if (topK > MAX_ALLOWED_TOP_K) {
            throw new IllegalArgumentException("topK cannot exceed " + MAX_ALLOWED_TOP_K + ", got: " + topK);
        }
        if (maxDistance != null && (maxDistance < 0.0 || maxDistance > 2.0)) {
            throw new IllegalArgumentException("maxDistance must be between 0.0 and 2.0, got: " + maxDistance);
        }

        String label = properties.getIndexLabel();
        String prop = properties.getIndexProperty();

        // 1. Check if vector index exists
        if (!vectorSchemaService.isVectorIndexPresent(label, prop)) {
            log.warn("Vector index on :{}({}) is not present in graph '{}'. Returning empty retrieval results.",
                    label, prop, graphClient.graphName());
            return Collections.emptyList();
        }

        // 2. Validate dimension matches configured embedding dimension (384)
        Integer existingDim = vectorSchemaService.getExistingVectorIndexDimension(label, prop);
        int expectedDim = embeddingService.getDimension();
        if (existingDim != null && existingDim != expectedDim) {
            throw new IllegalStateException("Vector index dimension mismatch on :" + label + "(" + prop + "): "
                    + "existing index has dimension " + existingDim + ", but embedding service produces " + expectedDim);
        }

        // 3. Generate query embedding vector
        List<Float> queryVector = embeddingService.embed(question);

        // 4. Execute native vector query via GraphClient
        // Cypher parameterized query linking DocumentChunk to its Document node
        String cypher = "CALL db.idx.vector.queryNodes($label, $property, $k, vecf32($queryVector)) YIELD node, score "
                + "MATCH (doc:Document)-[:HAS_CHUNK]->(node) "
                + "RETURN node.id AS chunkId, "
                + "       node.index AS chunkIndex, "
                + "       node.content AS content, "
                + "       coalesce(node.source_id, doc.source_id) AS sourceId, "
                + "       doc.title AS documentTitle, "
                + "       score "
                + "ORDER BY score ASC";

        Map<String, Object> params = Map.of(
                "label", label,
                "property", prop,
                "k", topK,
                "queryVector", queryVector
        );

        List<Map<String, Object>> rows;
        try {
            rows = graphClient.query(cypher, params);
        } catch (GraphUnavailableException e) {
            log.error("Failed to query vector index in FalkorDB: {}", e.getMessage());
            throw e;
        }

        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<RetrievedChunk> results = new ArrayList<>();
        Set<String> seenChunkIds = new HashSet<>();

        for (Map<String, Object> row : rows) {
            String chunkId = str(row, "chunkId");
            if (chunkId == null || !seenChunkIds.add(chunkId)) {
                // Skip null or already seen chunk IDs to prevent duplicate traversal artifacts
                continue;
            }

            int chunkIndex = intVal(row, "chunkIndex");
            String content = str(row, "content");
            String sourceId = str(row, "sourceId");
            String documentTitle = str(row, "documentTitle");
            double score = doubleVal(row, "score");
            double similarity = 1.0 - score;

            if (maxDistance != null && score > maxDistance) {
                // Result exceeds maximum allowable distance score cutoff
                continue;
            }

            Map<String, Object> metadata = Map.of(
                    "distance", score,
                    "similarity", similarity,
                    "chunkIndex", chunkIndex
            );

            results.add(new RetrievedChunk(
                    chunkId,
                    chunkIndex,
                    content != null ? content : "",
                    sourceId != null ? sourceId : "",
                    documentTitle != null ? documentTitle : "",
                    score,
                    similarity,
                    metadata
            ));
        }

        return results;
    }

    private static String str(Map<String, Object> row, String key) {
        Object val = row.get(key);
        return val == null ? null : String.valueOf(val);
    }

    private static int intVal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number n) {
            return n.intValue();
        }
        if (val != null) {
            try {
                return Integer.parseInt(val.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0;
    }

    private static double doubleVal(Map<String, Object> row, String key) {
        Object val = row.get(key);
        if (val instanceof Number n) {
            return n.doubleValue();
        }
        if (val != null) {
            try {
                return Double.parseDouble(val.toString());
            } catch (NumberFormatException ignored) {
            }
        }
        return 0.0;
    }
}
