package com.companybrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Set;

/**
 * Configuration properties for FalkorDB native vector indexing.
 * Does not assume an embedding dimension by default until an embedding provider is chosen.
 */
@Component
@ConfigurationProperties(prefix = "falkordb.vector")
public class VectorSchemaProperties {

    private static final Set<String> SUPPORTED_SIMILARITY_FUNCTIONS = Set.of("cosine", "euclidean");

    /**
     * Embedding dimension (e.g. 384 for all-MiniLM-L6-v2, 768 for Gemini, 1536 for OpenAI).
     * Must be explicitly configured and positive before vector index initialization.
     */
    private Integer dimension;

    /**
     * Distance / similarity function: 'cosine' or 'euclidean'.
     * Defaults to 'cosine'.
     */
    private String similarityFunction = "cosine";

    /**
     * Node label for document chunks. Defaults to 'DocumentChunk'.
     */
    private String indexLabel = "DocumentChunk";

    /**
     * Property on the node label storing the vector embedding. Defaults to 'embedding'.
     */
    private String indexProperty = "embedding";

    public VectorSchemaProperties() {
    }

    public VectorSchemaProperties(Integer dimension, String similarityFunction, String indexLabel, String indexProperty) {
        this.dimension = dimension;
        this.similarityFunction = similarityFunction;
        this.indexLabel = indexLabel;
        this.indexProperty = indexProperty;
    }

    public Integer getDimension() {
        return dimension;
    }

    public void setDimension(Integer dimension) {
        this.dimension = dimension;
    }

    public String getSimilarityFunction() {
        return similarityFunction;
    }

    public void setSimilarityFunction(String similarityFunction) {
        this.similarityFunction = similarityFunction;
    }

    public String getIndexLabel() {
        return indexLabel;
    }

    public void setIndexLabel(String indexLabel) {
        this.indexLabel = indexLabel;
    }

    public String getIndexProperty() {
        return indexProperty;
    }

    public void setIndexProperty(String indexProperty) {
        this.indexProperty = indexProperty;
    }

    /**
     * Validates configuration properties.
     * Throws IllegalStateException if dimension is null/unconfigured.
     * Throws IllegalArgumentException if any property is out of valid range or invalid format.
     */
    public void validate() {
        if (dimension == null) {
            throw new IllegalStateException("Vector embedding dimension is not configured. "
                    + "Please set 'falkordb.vector.dimension' (e.g. 384 for all-MiniLM-L6-v2, 768 for Gemini, 1536 for OpenAI).");
        }
        if (dimension <= 0 || dimension > 4096) {
            throw new IllegalArgumentException("Vector dimension must be between 1 and 4096. Given: " + dimension);
        }
        if (similarityFunction == null || !SUPPORTED_SIMILARITY_FUNCTIONS.contains(similarityFunction.trim().toLowerCase())) {
            throw new IllegalArgumentException("Unsupported similarity function: '" + similarityFunction
                    + "'. Supported functions: " + SUPPORTED_SIMILARITY_FUNCTIONS);
        }
        if (indexLabel == null || !indexLabel.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid index label: '" + indexLabel
                    + "'. Must be a valid alphanumeric identifier.");
        }
        if (indexProperty == null || !indexProperty.matches("^[A-Za-z][A-Za-z0-9_]*$")) {
            throw new IllegalArgumentException("Invalid index property: '" + indexProperty
                    + "'. Must be a valid alphanumeric identifier.");
        }
    }
}
