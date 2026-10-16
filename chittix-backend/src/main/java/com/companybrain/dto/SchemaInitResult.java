package com.companybrain.dto;

/**
 * Result of an idempotent vector schema initialization or inspection operation.
 */
public record SchemaInitResult(
        boolean created,
        String label,
        String property,
        int dimension,
        String similarityFunction,
        String message
) {
    public static SchemaInitResult created(String label, String property, int dimension, String sim) {
        return new SchemaInitResult(true, label, property, dimension, sim, "Vector index created successfully.");
    }

    public static SchemaInitResult alreadyExists(String label, String property, int dimension, String sim) {
        return new SchemaInitResult(false, label, property, dimension, sim, "Vector index already exists.");
    }
}
