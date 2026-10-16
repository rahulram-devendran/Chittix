package com.companybrain.service;

import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.SchemaInitResult;
import com.companybrain.dto.TextChunk;
import com.companybrain.exception.GraphUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DocumentIngestionServiceTest {

    @Mock
    GraphClient graphClient;

    @Mock
    VectorSchemaService vectorSchemaService;

    @Mock
    TextChunker textChunker;

    @Mock
    EmbeddingService embeddingService;

    DocumentIngestionService service;

    @BeforeEach
    void setUp() {
        service = new DocumentIngestionService(graphClient, vectorSchemaService, textChunker, embeddingService);
    }

    @Test
    void rejectsBlankSourceIdOrContent() {
        assertThrows(IllegalArgumentException.class, () -> service.ingestDocument(null, "content"));
        assertThrows(IllegalArgumentException.class, () -> service.ingestDocument("  ", "content"));
        assertThrows(IllegalArgumentException.class, () -> service.ingestDocument("DOC-001", null));
        assertThrows(IllegalArgumentException.class, () -> service.ingestDocument("DOC-001", "   "));
    }

    @Test
    void throwsExceptionWhenSourceDocumentDoesNotExistInGraph() {
        when(graphClient.query(anyString(), anyMap())).thenReturn(Collections.emptyList());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.ingestDocument("NON_EXISTENT", "Some content"));

        assertTrue(ex.getMessage().contains("Source document not found in graph"));
        verify(graphClient, never()).executeWrite(anyString(), anyMap());
    }

    @Test
    void failsSafelyWhenVectorIndexHasDimensionMismatch() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("title", "Payment Architecture Design")));
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(1536);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> service.ingestDocument("DOC-001", "Valid content"));

        assertTrue(ex.getMessage().contains("already exists with dimension 1536"));
        assertTrue(ex.getMessage().contains("required dimension is 384"));
        verify(vectorSchemaService, never()).dropVectorIndex(anyString(), anyString());
    }

    @Test
    void initializesVectorIndexWhenAbsentBeforeIngestion() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("title", "Payment Architecture Design")));
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(false);
        when(vectorSchemaService.initializeVectorIndex(384, "cosine", "DocumentChunk", "embedding"))
                .thenReturn(SchemaInitResult.created("DocumentChunk", "embedding", 384, "cosine"));
        when(textChunker.chunk("Document text"))
                .thenReturn(List.of(new TextChunk(0, "Document text")));
        when(embeddingService.embed("Document text"))
                .thenReturn(List.of(0.1f, 0.2f, 0.3f));

        DocumentIngestionResult result = service.ingestDocument("DOC-001", "Document text");

        assertEquals("INGESTED", result.status());
        assertEquals(1, result.chunkCount());
        assertEquals("DOC-001", result.sourceId());
        assertEquals("Payment Architecture Design", result.documentTitle());
        verify(vectorSchemaService).initializeVectorIndex(384, "cosine", "DocumentChunk", "embedding");
    }

    @Test
    void successfullyIngestsDocumentChunksAndAppliesMerge() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("title", "Payment Architecture Design")));
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);

        TextChunk chunk0 = new TextChunk(0, "Chunk 0 content");
        TextChunk chunk1 = new TextChunk(1, "Chunk 1 content");
        when(textChunker.chunk("Multi-chunk text")).thenReturn(List.of(chunk0, chunk1));
        when(embeddingService.embed("Chunk 0 content")).thenReturn(List.of(0.1f, 0.2f));
        when(embeddingService.embed("Chunk 1 content")).thenReturn(List.of(0.3f, 0.4f));

        DocumentIngestionResult result = service.ingestDocument("DOC-001", "Multi-chunk text");

        assertEquals("INGESTED", result.status());
        assertEquals(2, result.chunkCount());

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        // 2 chunk upserts + 1 prune query
        verify(graphClient, times(3)).executeWrite(cypherCaptor.capture(), paramsCaptor.capture());

        List<String> queries = cypherCaptor.getAllValues();
        List<Map<String, Object>> allParams = paramsCaptor.getAllValues();

        assertTrue(queries.get(0).contains("MERGE (c:DocumentChunk {id: $chunkId})"));
        assertTrue(queries.get(0).contains("vecf32($embedding)"));
        assertEquals("DOC-001#chunk-0", allParams.get(0).get("chunkId"));
        assertEquals("DOC-001#chunk-1", allParams.get(1).get("chunkId"));

        // Pruning query for stale chunks
        assertTrue(queries.get(2).contains("WHERE c.index >= $newChunkCount"));
        assertEquals(2, allParams.get(2).get("newChunkCount"));
    }

    @Test
    void reingestionUpdatesExistingChunksWithoutDuplication() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("title", "Payment Architecture Design")));
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);

        TextChunk chunk = new TextChunk(0, "Updated content");
        when(textChunker.chunk("Updated content")).thenReturn(List.of(chunk));
        when(embeddingService.embed("Updated content")).thenReturn(List.of(0.5f, 0.6f));

        DocumentIngestionResult result = service.ingestDocument("DOC-001", "Updated content");

        assertEquals("INGESTED", result.status());
        assertEquals(1, result.chunkCount());

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(graphClient, times(2)).executeWrite(cypherCaptor.capture(), paramsCaptor.capture());

        // Uses MERGE with ON MATCH SET for idempotency
        assertTrue(cypherCaptor.getAllValues().get(0).contains("ON MATCH SET"));
        assertEquals("DOC-001#chunk-0", paramsCaptor.getAllValues().get(0).get("chunkId"));
    }

    @Test
    void propagatesGraphUnavailableExceptionOnWriteFailure() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(Map.of("title", "Payment Architecture Design")));
        when(vectorSchemaService.isVectorIndexPresent("DocumentChunk", "embedding")).thenReturn(true);
        when(vectorSchemaService.getExistingVectorIndexDimension("DocumentChunk", "embedding")).thenReturn(384);

        when(textChunker.chunk("Failing text")).thenReturn(List.of(new TextChunk(0, "Failing text")));
        when(embeddingService.embed("Failing text")).thenReturn(List.of(0.1f));
        when(graphClient.executeWrite(anyString(), anyMap()))
                .thenThrow(new GraphUnavailableException("FalkorDB connection dropped"));

        assertThrows(GraphUnavailableException.class, () -> service.ingestDocument("DOC-001", "Failing text"));
    }
}
