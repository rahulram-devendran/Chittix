package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.SchemaInitResult;
import com.companybrain.exception.GraphUnavailableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Service for safe, explicit, and idempotent FalkorDB vector schema operations.
 * Vector index creation is intentionally not run automatically on startup.
 */
@Service
public class VectorSchemaService {

    private static final Logger log = LoggerFactory.getLogger(VectorSchemaService.class);
    private static final Set<String> SUPPORTED_SIMILARITY_FUNCTIONS = Set.of("cosine", "euclidean");

    private final GraphClient graphClient;
    private final VectorSchemaProperties properties;

    public VectorSchemaService(GraphClient graphClient, VectorSchemaProperties properties) {
        this.graphClient = graphClient;
        this.properties = properties;
    }

    /**
     * Checks if a vector index is already operational for the given label and property.
     */
    public boolean isVectorIndexPresent(String label, String property) {
        return getExistingVectorIndexOptions(label, property) != null;
    }

    /**
     * Retrieves the options map for an existing vector index on label and property, or null if none.
     */
    @SuppressWarnings("unchecked")
    public Map<String, Object> getExistingVectorIndexOptions(String label, String property) {
        try {
            List<Map<String, Object>> indexes = graphClient.query("CALL db.indexes()", Collections.emptyMap());
            for (Map<String, Object> idx : indexes) {
                Object l = idx.get("label");
                Object props = idx.get("properties");
                if (label.equalsIgnoreCase(String.valueOf(l))) {
                    boolean matchesProp = false;
                    if (props instanceof List<?> list && list.stream().anyMatch(p -> property.equalsIgnoreCase(String.valueOf(p)))) {
                        matchesProp = true;
                    } else if (props != null && props.toString().contains(property)) {
                        matchesProp = true;
                    }
                    if (matchesProp) {
                        Object options = idx.get("options");
                        if (options instanceof Map<?, ?> map) {
                            Object propOptions = map.get(property);
                            if (propOptions instanceof Map<?, ?> propMap) {
                                return (Map<String, Object>) propMap;
                            }
                            return (Map<String, Object>) map;
                        }
                        return Collections.emptyMap();
                    }
                }
            }
            return null;
        } catch (GraphUnavailableException e) {
            log.warn("Could not query db.indexes() from FalkorDB: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extracts existing index dimension if present and discernable, or null.
     */
    public Integer getExistingVectorIndexDimension(String label, String property) {
        Map<String, Object> options = getExistingVectorIndexOptions(label, property);
        if (options == null) {
            return null;
        }
        Object dim = options.get("dimension");
        if (dim instanceof Number n) {
            return n.intValue();
        }
        if (dim != null) {
            try {
                return Integer.parseInt(dim.toString().trim());
            } catch (NumberFormatException ignored) {
            }
        }
        return null;
    }

    /**
     * Idempotently initializes the vector index using configured properties.
     * Validates that dimension is configured before execution.
     */
    public SchemaInitResult initializeVectorIndex() {
        properties.validate();
        return initializeVectorIndex(
                properties.getDimension(),
                properties.getSimilarityFunction(),
                properties.getIndexLabel(),
                properties.getIndexProperty()
        );
    }

    /**
     * Idempotently initializes a vector index with explicit parameters.
     * Verifies that existing index does not have a dimension mismatch.
     */
    public SchemaInitResult initializeVectorIndex(int dimension, String similarityFunction, String label, String property) {
        validateInputs(dimension, similarityFunction, label, property);

        String simFunc = similarityFunction.trim().toLowerCase();

        // 1. Idempotency & dimension mismatch check via catalog
        if (isVectorIndexPresent(label, property)) {
            Integer existingDim = getExistingVectorIndexDimension(label, property);
            if (existingDim != null && existingDim != dimension) {
                throw new IllegalStateException("Vector index on :" + label + "(" + property + ") already exists with dimension "
                        + existingDim + ", but target dimension is " + dimension
                        + ". Mismatch detected; manual resolution is required. Automatic drop or recreation is not permitted.");
            }
            log.info("Vector index on :{}({}) already exists in graph '{}'.", label, property, graphClient.graphName());
            return SchemaInitResult.alreadyExists(label, property, dimension, simFunc);
        }

        // 2. Execute parameterized vector index creation
        String cypher = "CREATE VECTOR INDEX FOR (c:" + label + ") ON (c." + property + ") "
                + "OPTIONS {dimension: $dimension, similarityFunction: $similarityFunction}";
        Map<String, Object> params = Map.of(
                "dimension", dimension,
                "similarityFunction", simFunc
        );

        try {
            graphClient.executeWrite(cypher, params);
            log.info("Successfully created vector index for :{}({}) in graph '{}' with dimension {} and {}.",
                    label, property, graphClient.graphName(), dimension, simFunc);
            return SchemaInitResult.created(label, property, dimension, simFunc);
        } catch (GraphUnavailableException e) {
            if (e.getMessage() != null && e.getMessage().toLowerCase().contains("already indexed")) {
                Integer existingDim = getExistingVectorIndexDimension(label, property);
                if (existingDim != null && existingDim != dimension) {
                    throw new IllegalStateException("Vector index on :" + label + "(" + property + ") exists with dimension "
                            + existingDim + ", but target dimension is " + dimension
                            + ". Mismatch detected; manual resolution is required.");
                }
                log.info("Vector index on :{}({}) was concurrently created in graph '{}'.", label, property, graphClient.graphName());
                return SchemaInitResult.alreadyExists(label, property, dimension, simFunc);
            }
            throw e;
        }
    }

    /**
     * Explicitly drops the vector index if present.
     */
    public boolean dropVectorIndex(String label, String property) {
        if (!label.matches("^[A-Za-z][A-Za-z0-9_]*$") || !property.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid label or property identifier.");
        }
        if (!isVectorIndexPresent(label, property)) {
            return false;
        }
        String cypher = "DROP INDEX ON :" + label + "(" + property + ")";
        graphClient.executeWrite(cypher, Collections.emptyMap());
        log.info("Dropped vector index on :{}({}) in graph '{}'.", label, property, graphClient.graphName());
        return true;
    }

    private void validateInputs(int dimension, String similarityFunction, String label, String property) {
        if (dimension <= 0 || dimension > 4096) {
            throw new IllegalArgumentException("Vector dimension must be between 1 and 4096. Given: " + dimension);
        }
        if (similarityFunction == null || !SUPPORTED_SIMILARITY_FUNCTIONS.contains(similarityFunction.trim().toLowerCase())) {
            throw new IllegalArgumentException("Unsupported similarity function: '" + similarityFunction
                    + "'. Supported functions: " + SUPPORTED_SIMILARITY_FUNCTIONS);
        }
        if (label == null || !label.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid index label: '" + label + "'. Must be a valid alphanumeric identifier.");
        }
        if (property == null || !property.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid index property: '" + property + "'. Must be a valid alphanumeric identifier.");
        }
    }
}
