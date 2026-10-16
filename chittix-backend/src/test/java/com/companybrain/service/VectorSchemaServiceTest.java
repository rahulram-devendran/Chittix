package com.companybrain.service;

import com.companybrain.config.VectorSchemaProperties;
import com.companybrain.dto.SchemaInitResult;
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
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class VectorSchemaServiceTest {

    @Mock
    GraphClient graphClient;

    VectorSchemaProperties properties;
    VectorSchemaService service;

    @BeforeEach
    void setUp() {
        org.mockito.Mockito.lenient().when(graphClient.graphName()).thenReturn("company_brain");
        properties = new VectorSchemaProperties(768, "cosine", "DocumentChunk", "embedding");
        service = new VectorSchemaService(graphClient, properties);
    }

    @Test
    void isVectorIndexPresentReturnsTrueWhenIndexExistsInCatalog() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(List.of(
                        Map.of("label", "DocumentChunk", "properties", List.of("embedding"), "types", Map.of("embedding", List.of("VECTOR")))
                ));

        assertTrue(service.isVectorIndexPresent("DocumentChunk", "embedding"));
    }

    @Test
    void isVectorIndexPresentReturnsFalseWhenNoMatchingIndex() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(List.of(
                        Map.of("label", "OtherNode", "properties", List.of("name"))
                ));

        assertFalse(service.isVectorIndexPresent("DocumentChunk", "embedding"));
    }

    @Test
    void initializeVectorIndexCreatesIndexWhenNoneExists() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(Collections.emptyList());
        when(graphClient.executeWrite(anyString(), anyMap()))
                .thenReturn(Collections.emptyList());

        SchemaInitResult result = service.initializeVectorIndex();

        assertTrue(result.created());
        assertEquals("DocumentChunk", result.label());
        assertEquals("embedding", result.property());
        assertEquals(768, result.dimension());
        assertEquals("cosine", result.similarityFunction());

        ArgumentCaptor<String> cypherCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Map<String, Object>> paramsCaptor = ArgumentCaptor.forClass(Map.class);
        verify(graphClient).executeWrite(cypherCaptor.capture(), paramsCaptor.capture());

        assertTrue(cypherCaptor.getValue().contains("CREATE VECTOR INDEX FOR (c:DocumentChunk) ON (c.embedding)"));
        assertTrue(cypherCaptor.getValue().contains("$dimension"));
        assertTrue(cypherCaptor.getValue().contains("$similarityFunction"));
        assertEquals(768, paramsCaptor.getValue().get("dimension"));
        assertEquals("cosine", paramsCaptor.getValue().get("similarityFunction"));
    }

    @Test
    void initializeVectorIndexIsIdempotentWhenIndexAlreadyExists() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(List.of(
                        Map.of("label", "DocumentChunk", "properties", List.of("embedding"))
                ));

        SchemaInitResult result = service.initializeVectorIndex();

        assertFalse(result.created());
        assertTrue(result.message().contains("already exists"));
        verify(graphClient, never()).executeWrite(anyString(), anyMap());
    }

    @Test
    void initializeVectorIndexHandlesConcurrentAlreadyIndexedExceptionGracefully() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(Collections.emptyList());
        when(graphClient.executeWrite(anyString(), anyMap()))
                .thenThrow(new GraphUnavailableException("Attribute 'embedding' is already indexed"));

        SchemaInitResult result = service.initializeVectorIndex();

        assertFalse(result.created());
        assertTrue(result.message().contains("already exists"));
    }

    @Test
    void initializeVectorIndexThrowsWhenDimensionUnconfigured() {
        properties.setDimension(null);

        assertThrows(IllegalStateException.class, () -> service.initializeVectorIndex());
        verify(graphClient, never()).executeWrite(anyString(), anyMap());
    }

    @Test
    void dropVectorIndexDropsWhenPresent() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(List.of(
                        Map.of("label", "DocumentChunk", "properties", List.of("embedding"))
                ));
        when(graphClient.executeWrite(anyString(), anyMap()))
                .thenReturn(Collections.emptyList());

        boolean dropped = service.dropVectorIndex("DocumentChunk", "embedding");

        assertTrue(dropped);
        verify(graphClient).executeWrite("DROP INDEX ON :DocumentChunk(embedding)", Collections.emptyMap());
    }

    @Test
    void dropVectorIndexReturnsFalseWhenNotPresent() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(Collections.emptyList());

        boolean dropped = service.dropVectorIndex("DocumentChunk", "embedding");

        assertFalse(dropped);
        verify(graphClient, never()).executeWrite(anyString(), anyMap());
    }

    @Test
    void initializeVectorIndexThrowsWhenExistingDimensionMismatches() {
        when(graphClient.query(eq("CALL db.indexes()"), eq(Collections.emptyMap())))
                .thenReturn(List.of(
                        Map.of(
                                "label", "DocumentChunk",
                                "properties", List.of("embedding"),
                                "options", Map.of("embedding", Map.of("dimension", 1536))
                        )
                ));

        IllegalStateException ex = assertThrows(IllegalStateException.class, () -> service.initializeVectorIndex());
        assertTrue(ex.getMessage().contains("already exists with dimension 1536"));
        assertTrue(ex.getMessage().contains("target dimension is 768"));
        verify(graphClient, never()).executeWrite(anyString(), anyMap());
    }
}
