package com.companybrain.service;

import com.companybrain.dto.TextChunk;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Configurable text chunking service that splits documents into overlapping passages
 * while avoiding splitting words where practical.
 */
@Service
public class TextChunker {

    public static final int DEFAULT_CHUNK_SIZE = 400;
    public static final int DEFAULT_CHUNK_OVERLAP = 60;

    private final int chunkSize;
    private final int chunkOverlap;

    public TextChunker() {
        this(DEFAULT_CHUNK_SIZE, DEFAULT_CHUNK_OVERLAP);
    }

    public TextChunker(@Value("${falkordb.chunking.size:400}") int chunkSize,
                       @Value("${falkordb.chunking.overlap:60}") int chunkOverlap) {
        if (chunkSize <= 0) {
            throw new IllegalArgumentException("chunkSize must be greater than 0, got: " + chunkSize);
        }
        if (chunkOverlap < 0) {
            throw new IllegalArgumentException("chunkOverlap must be non-negative, got: " + chunkOverlap);
        }
        if (chunkOverlap >= chunkSize) {
            throw new IllegalArgumentException("chunkOverlap (" + chunkOverlap + ") must be less than chunkSize (" + chunkSize + ")");
        }
        this.chunkSize = chunkSize;
        this.chunkOverlap = chunkOverlap;
    }

    public int getChunkSize() {
        return chunkSize;
    }

    public int getChunkOverlap() {
        return chunkOverlap;
    }

    /**
     * Splits text into overlapping chunks.
     * Returns an empty list if text is null or blank.
     */
    public List<TextChunk> chunk(String text) {
        if (text == null || text.isBlank()) {
            return Collections.emptyList();
        }

        String normalized = text.replace("\r\n", "\n").trim();
        if (normalized.isEmpty()) {
            return Collections.emptyList();
        }

        if (normalized.length() <= chunkSize) {
            return List.of(new TextChunk(0, normalized));
        }

        List<TextChunk> chunks = new ArrayList<>();
        int start = 0;
        int chunkIndex = 0;
        int textLength = normalized.length();

        while (start < textLength) {
            int end = Math.min(start + chunkSize, textLength);

            if (end < textLength) {
                // Look backwards up to 25% of chunkSize for a whitespace word boundary
                int searchFloor = Math.max(start + 1, end - (int) (chunkSize * 0.25));
                int bestBreak = -1;
                for (int i = end; i >= searchFloor; i--) {
                    if (Character.isWhitespace(normalized.charAt(i))) {
                        bestBreak = i;
                        break;
                    }
                }
                if (bestBreak > start) {
                    end = bestBreak;
                }
            }

            String chunkContent = normalized.substring(start, end).trim();
            if (!chunkContent.isEmpty()) {
                chunks.add(new TextChunk(chunkIndex++, chunkContent));
            }

            if (end >= textLength) {
                break;
            }

            // Calculate next start with overlap
            int nextStart = end - chunkOverlap;
            if (nextStart <= start) {
                nextStart = start + 1; // Strict progress guarantee
            }

            // Skip leading whitespace for the next chunk
            while (nextStart < textLength && Character.isWhitespace(normalized.charAt(nextStart))) {
                nextStart++;
            }

            start = nextStart;
        }

        return chunks;
    }
}
