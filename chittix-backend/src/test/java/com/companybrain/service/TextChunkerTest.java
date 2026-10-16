package com.companybrain.service;

import com.companybrain.dto.TextChunk;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TextChunkerTest {

    @Test
    void returnsEmptyListForNullOrBlankInput() {
        TextChunker chunker = new TextChunker(100, 20);

        assertTrue(chunker.chunk(null).isEmpty());
        assertTrue(chunker.chunk("").isEmpty());
        assertTrue(chunker.chunk("   \n\t  ").isEmpty());
    }

    @Test
    void returnsSingleChunkWhenTextFitsWithinChunkSize() {
        TextChunker chunker = new TextChunker(100, 20);
        String text = "PostgreSQL was selected for transactional integrity.";

        List<TextChunk> chunks = chunker.chunk(text);

        assertEquals(1, chunks.size());
        assertEquals(0, chunks.get(0).index());
        assertEquals(text, chunks.get(0).content());
    }

    @Test
    void chunksLongTextAndPreservesWordBoundaries() {
        TextChunker chunker = new TextChunker(60, 15);
        String text = "PostgreSQL provides robust ACID guarantees, excellent support for JSON, "
                + "and mature connection pooling needed by high-throughput payment ledgers.";

        List<TextChunk> chunks = chunker.chunk(text);

        assertTrue(chunks.size() > 1, "Expected multiple chunks, got " + chunks.size());
        for (int i = 0; i < chunks.size(); i++) {
            assertEquals(i, chunks.get(i).index());
            assertFalse(chunks.get(i).content().isBlank());
            // No chunk should exceed chunkSize (tolerance for edge cases)
            assertTrue(chunks.get(i).content().length() <= 60, "Chunk exceeded size: " + chunks.get(i).content());
        }
    }

    @Test
    void preservesOverlapBetweenConsecutiveChunks() {
        TextChunker chunker = new TextChunker(50, 15);
        String text = "Alpha beta gamma delta epsilon zeta eta theta iota kappa lambda mu nu xi omicron pi rho sigma tau.";

        List<TextChunk> chunks = chunker.chunk(text);

        assertTrue(chunks.size() >= 2);
        // The second chunk should contain text from the tail of the first chunk
        String first = chunks.get(0).content();
        String second = chunks.get(1).content();
        // Extract words from first chunk
        String[] firstWords = first.split("\\s+");
        String lastWordOfFirst = firstWords[firstWords.length - 1];

        assertTrue(second.contains(lastWordOfFirst) || first.contains(second.split("\\s+")[0]),
                "Expected overlap between chunk 0 ('" + first + "') and chunk 1 ('" + second + "')");
    }

    @Test
    void handlesSingleLongWordWithoutWhitespaceGracefully() {
        TextChunker chunker = new TextChunker(20, 5);
        String longWord = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";

        List<TextChunk> chunks = chunker.chunk(longWord);

        assertFalse(chunks.isEmpty());
        assertEquals(0, chunks.get(0).index());
        assertEquals(20, chunks.get(0).content().length());
    }

    @Test
    void rejectsInvalidConfigurations() {
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(0, 0));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(-50, 10));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, -10));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 100));
        assertThrows(IllegalArgumentException.class, () -> new TextChunker(100, 120));
    }
}
