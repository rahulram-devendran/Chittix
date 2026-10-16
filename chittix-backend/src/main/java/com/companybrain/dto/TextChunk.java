package com.companybrain.dto;

/**
 * Represents a single text passage chunk with its sequential zero-based index.
 */
public record TextChunk(int index, String content) {
    public TextChunk {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        if (content == null || content.isBlank()) {
            throw new IllegalArgumentException("content must not be blank");
        }
    }
}
