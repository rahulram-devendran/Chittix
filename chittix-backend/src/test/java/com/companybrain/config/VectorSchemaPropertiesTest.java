package com.companybrain.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class VectorSchemaPropertiesTest {

    @Test
    void validatesSuccessfullyWithExplicitProperties() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                1536, "cosine", "DocumentChunk", "embedding"
        );
        assertDoesNotThrow(props::validate);
    }

    @Test
    void validatesSuccessfullyWithAlternativeSupportedSimilarity() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                384, "euclidean", "DocChunk", "vec"
        );
        assertDoesNotThrow(props::validate);
    }

    @Test
    void throwsIllegalStateExceptionWhenDimensionIsUnconfigured() {
        VectorSchemaProperties props = new VectorSchemaProperties();
        // dimension is null
        IllegalStateException ex = assertThrows(IllegalStateException.class, props::validate);
        assertTrue(ex.getMessage().contains("Vector embedding dimension is not configured"));
    }

    @Test
    void throwsIllegalArgumentExceptionWhenDimensionIsZeroOrNegative() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                0, "cosine", "DocumentChunk", "embedding"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, props::validate);
        assertTrue(ex.getMessage().contains("between 1 and 4096"));

        props.setDimension(-10);
        assertThrows(IllegalArgumentException.class, props::validate);
    }

    @Test
    void throwsIllegalArgumentExceptionWhenDimensionExceedsLimit() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                5000, "cosine", "DocumentChunk", "embedding"
        );
        assertThrows(IllegalArgumentException.class, props::validate);
    }

    @Test
    void throwsIllegalArgumentExceptionWhenSimilarityFunctionIsUnsupported() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                768, "manhattan", "DocumentChunk", "embedding"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, props::validate);
        assertTrue(ex.getMessage().contains("Unsupported similarity function"));
    }

    @Test
    void throwsIllegalArgumentExceptionWhenIndexLabelIsInvalidIdentifier() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                768, "cosine", "123Bad-Label; DROP GRAPH", "embedding"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, props::validate);
        assertTrue(ex.getMessage().contains("Invalid index label"));
    }

    @Test
    void throwsIllegalArgumentExceptionWhenIndexPropertyIsInvalidIdentifier() {
        VectorSchemaProperties props = new VectorSchemaProperties(
                768, "cosine", "DocumentChunk", "embed; DETACH DELETE n"
        );
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, props::validate);
        assertTrue(ex.getMessage().contains("Invalid index property"));
    }
}
