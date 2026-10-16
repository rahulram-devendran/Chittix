package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.RetrievedChunk;
import com.companybrain.exception.GraphUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorRetrievalServiceTest {

    @Mock
    GraphClient graphClient;

    @Mock
    VectorSchemaService vectorSchemaService;

    @Mock
    EmbeddingService embeddingService;

    VectorSchemaProperties properties;
    VectorRetrievalService service;

    private static final List<Float> DUMMY_VECTOR = List.of(0.1f, 0.2f, 0.3f);

    @BeforeEach
    void setUp() {
        lenient().when(graphClient.graphName()).thenReturn("company_brain");
        lenient().when(embeddingService.getDimension()).thenReturn(384);
        properties = new VectorSchemaProperties(384, "cosine", "DocumentChunk", "embedding");
        service = new VectorRetrievalService(graphClient, vectorSchemaService, embeddingService, properties, null);
    }

    @Test
    void rejectsNullOrBlankQuestion() {
        assertThrows(IllegalArgumentException.class, () -> service.retrieve(null));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve(""));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("   \t\n "));
    }

    @Test
    void rejectsInvalidTopKValues() {
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("Why PostgreSQL?", 0));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("Why PostgreSQL?", -5));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("Why PostgreSQL?", 101));
    }

    @Test
    void rejectsInvalidMaxDistance() {
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("Why PostgreSQL?", 5, -0.1));
        assertThrows(IllegalArgumentException.class, () -> service.retrieve("Why PostgreSQL?", 5, 2.5));
    }

    @Test
    void returnsEmptyListWhenVectorIndexIsNotPresent() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(false);

        List<RetrievedChunk> results = service.retrieve("Why PostgreSQL?");

        assertTrue(results.isEmpty());
        verify(embeddingService, never()).embed(anyString());
        verify(graphClient, never()).query(anyString(), anyMap());
    }

    @Test
    void throwsIllegalStateExceptionWhenExistingIndexHasDimensionMismatch() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(1536);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.retrieve("Why PostgreSQL?"));

        assertTrue(ex.getMessage().contains("Vector index dimension mismatch"));
        assertTrue(ex.getMessage().contains("1536"));
        assertTrue(ex.getMessage().contains("384"));
        verify(graphClient, never()).query(anyString(), anyMap());
    }

    @Test
    void retrievesAndParsesChunksWithVerifiedScoreSemantics() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);
        when(embeddingService.embed("Why PostgreSQL?")).thenReturn(DUMMY_VECTOR);

        // FalkorDB cosine distance: lower is closer (0.05 is closer than 0.20)
        Map<String, Object> row1 = Map.of(
                "chunkId", "DOC-001#chunk-0",
                "chunkIndex", 0,
                "content", "PostgreSQL guarantees ACID transactions.",
                "sourceId", "DOC-001",
                "documentTitle", "Payment Architecture Design",
                "score", 0.05
        );
        Map<String, Object> row2 = Map.of(
                "chunkId", "DOC-001#chunk-1",
                "chunkIndex", 1,
                "content", "Connection pooling is managed via PgBouncer.",
                "sourceId", "DOC-001",
                "documentTitle", "Payment Architecture Design",
                "score", 0.20
        );

        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(row1, row2));

        List<RetrievedChunk> results = service.retrieve("Why PostgreSQL?", 5);

        assertEquals(2, results.size());

        // First chunk (distance 0.05 -> similarity 0.95)
        RetrievedChunk first = results.get(0);
        assertEquals("DOC-001#chunk-0", first.chunkId());
        assertEquals(0, first.chunkIndex());
        assertEquals("PostgreSQL guarantees ACID transactions.", first.content());
        assertEquals("DOC-001", first.sourceId());
        assertEquals("Payment Architecture Design", first.documentTitle());
        assertEquals(0.05, first.score(), 0.0001);
        assertEquals(0.95, first.similarityScore(), 0.0001);
        assertNotNull(first.metadata());
        assertEquals(0.05, first.metadata().get("distance"));

        // Second chunk (distance 0.20 -> similarity 0.80)
        RetrievedChunk second = results.get(1);
        assertEquals("DOC-001#chunk-1", second.chunkId());
        assertEquals(0.20, second.score(), 0.0001);
        assertEquals(0.80, second.similarityScore(), 0.0001);

        // Result ordering: lower distance first
        assertTrue(first.score() < second.score());
        assertTrue(first.similarityScore() > second.similarityScore());
    }

    @Test
    void deduplicatesChunksIfGraphTraversalReturnsDuplicateRows() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);
        when(embeddingService.embed("Why PostgreSQL?")).thenReturn(DUMMY_VECTOR);

        Map<String, Object> duplicateRow = Map.of(
                "chunkId", "DOC-001#chunk-0",
                "chunkIndex", 0,
                "content", "PostgreSQL guarantees ACID transactions.",
                "sourceId", "DOC-001",
                "documentTitle", "Payment Architecture Design",
                "score", 0.05
        );

        // Multiple rows with identical chunkId (e.g. from multiple paths or join duplication)
        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(duplicateRow, duplicateRow));

        List<RetrievedChunk> results = service.retrieve("Why PostgreSQL?", 5);

        assertEquals(1, results.size(), "Expected deduplicated chunk result list");
        assertEquals("DOC-001#chunk-0", results.get(0).chunkId());
    }

    @Test
    void filtersResultsExceedingMaxDistanceCutoff() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);
        when(embeddingService.embed("Why PostgreSQL?")).thenReturn(DUMMY_VECTOR);

        Map<String, Object> closeRow = Map.of(
                "chunkId", "DOC-001#chunk-0",
                "chunkIndex", 0,
                "content", "Close match",
                "sourceId", "DOC-001",
                "documentTitle", "Doc 1",
                "score", 0.30
        );
        Map<String, Object> farRow = Map.of(
                "chunkId", "DOC-002#chunk-0",
                "chunkIndex", 0,
                "content", "Far match",
                "sourceId", "DOC-002",
                "documentTitle", "Doc 2",
                "score", 0.85
        );

        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(closeRow, farRow));

        // Cutoff at max distance 0.50
        List<RetrievedChunk> filtered = service.retrieve("Why PostgreSQL?", 5, 0.50);

        assertEquals(1, filtered.size());
        assertEquals("DOC-001#chunk-0", filtered.get(0).chunkId());
    }

    @Test
    void returnsEmptyListWhenGraphReturnsNoRows() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);
        when(embeddingService.embed("Unrelated question")).thenReturn(DUMMY_VECTOR);
        when(graphClient.query(anyString(), anyMap())).thenReturn(Collections.emptyList());

        List<RetrievedChunk> results = service.retrieve("Unrelated question", 5);

        assertTrue(results.isEmpty());
    }

    @Test
    void propagatesGraphUnavailableExceptionGracefully() {
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);
        when(embeddingService.embed("Why PostgreSQL?")).thenReturn(DUMMY_VECTOR);
        when(graphClient.query(anyString(), anyMap()))
                .thenThrow(new GraphUnavailableException("FalkorDB connection timeout"));

        assertThrows(GraphUnavailableException.class, () -> service.retrieve("Why PostgreSQL?"));
    }
}
