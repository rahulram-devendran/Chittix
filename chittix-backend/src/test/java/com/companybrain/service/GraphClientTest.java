package com.companybrain.service;

import com.companybrain.exception.GraphUnavailableException;
import com.falkordb.Driver;
import com.falkordb.GraphContextGenerator;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GraphClientTest {

    @Mock
    Driver driver;

    @Mock
    GraphContextGenerator graph;

    @Mock
    ResultSet resultSet;

    @Mock
    Record record;

    GraphClient graphClient;

    @BeforeEach
    void setUp() {
        when(driver.graph("test_graph")).thenReturn(graph);
        graphClient = new GraphClient(driver, "test_graph");
    }

    @Test
    void queryExecutesReadOnlyQueryWithParameters() {
        when(graph.readOnlyQuery(eq("MATCH (n) RETURN n.name AS name"), anyMap())).thenReturn(resultSet);
        when(resultSet.iterator()).thenReturn(List.of(record).iterator());
        when(record.keys()).thenReturn(List.of("name"));
        when(record.getValue("name")).thenReturn("PostgreSQL");

        List<Map<String, Object>> result = graphClient.query(
                "MATCH (n) RETURN n.name AS name", Map.of("key", "val"));

        assertEquals(1, result.size());
        assertEquals("PostgreSQL", result.get(0).get("name"));
        verify(graph).readOnlyQuery("MATCH (n) RETURN n.name AS name", Map.of("key", "val"));
    }

    @Test
    void queryTranslatesRuntimeExceptionToGraphUnavailableException() {
        when(graph.readOnlyQuery(anyString(), anyMap())).thenThrow(new RuntimeException("Connection refused"));

        GraphUnavailableException ex = assertThrows(GraphUnavailableException.class,
                () -> graphClient.query("MATCH (n) RETURN n", Map.of()));
        assertTrue(ex.getMessage().contains("Connection refused"));
    }

    @Test
    void executeWriteExecutesWriteQueryWithParameters() {
        when(graph.query(eq("CREATE (n:Chunk {text: $text})"), anyMap())).thenReturn(resultSet);
        when(resultSet.iterator()).thenReturn(List.<Record>of().iterator());

        List<Map<String, Object>> result = graphClient.executeWrite(
                "CREATE (n:Chunk {text: $text})", Map.of("text", "hello"));

        assertTrue(result.isEmpty());
        verify(graph).query("CREATE (n:Chunk {text: $text})", Map.of("text", "hello"));
    }

    @Test
    void executeWriteTranslatesRuntimeException() {
        when(graph.query(anyString(), anyMap())).thenThrow(new RuntimeException("Write lock failure"));

        GraphUnavailableException ex = assertThrows(GraphUnavailableException.class,
                () -> graphClient.executeWrite("CREATE (n)", Map.of()));
        assertTrue(ex.getMessage().contains("Write lock failure"));
    }

    @Test
    void pingReturnsTrueWhenGraphReachable() {
        when(graph.readOnlyQuery(anyString(), anyMap())).thenReturn(resultSet);
        when(resultSet.iterator()).thenReturn(List.<Record>of().iterator());

        assertTrue(graphClient.ping());
    }

    @Test
    void pingReturnsTrueWhenEmptyGraphKey() {
        when(graph.readOnlyQuery(anyString(), anyMap()))
                .thenThrow(new RuntimeException("ERR Invalid graph operation on empty key"));

        assertTrue(graphClient.ping());
    }

    @Test
    void pingReturnsFalseWhenGraphUnavailable() {
        when(graph.readOnlyQuery(anyString(), anyMap())).thenThrow(new RuntimeException("Connection refused"));

        assertFalse(graphClient.ping());
        verify(graph, never()).query(anyString(), anyMap());
    }
}
