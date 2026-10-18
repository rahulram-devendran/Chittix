package com.companybrain.service;

import com.companybrain.exception.GraphUnavailableException;
import com.falkordb.Driver;
import com.falkordb.Graph;
import com.falkordb.Record;
import com.falkordb.ResultSet;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Thin wrapper over the JFalkorDB driver. Read-only; never modifies the graph. */
@Component
public class GraphClient {

    private final Driver driver;
    private final String graphName;

    public GraphClient(Driver driver, @Value("${falkordb.graph}") String graphName) {
        this.driver = driver;
        this.graphName = graphName;
    }

    public String graphName() {
        return graphName;
    }

    /** Runs a read-only Cypher query and returns each row as column-name -> value. */
    public List<Map<String, Object>> query(String cypher, Map<String, Object> params) {
        try {
            Graph graph = driver.graph(graphName);
            Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
            ResultSet resultSet = graph.readOnlyQuery(cypher, safeParams);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Record record : resultSet) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : record.keys()) {
                    row.put(key, record.getValue(key));
                }
                rows.add(row);
            }
            return rows;
        } catch (RuntimeException e) {
            throw new GraphUnavailableException(
                    "FalkorDB query on graph '" + graphName + "' failed: " + e.getMessage(), e);
        }
    }

    /**
     * Runs a write Cypher query using driver.graph(graphName).query(...) and returns
     * each row as column-name -> value (or an empty list if no RETURN clause is present).
     */
    public List<Map<String, Object>> executeWrite(String cypher, Map<String, Object> params) {
        try {
            Graph graph = driver.graph(graphName);
            Map<String, Object> safeParams = params == null ? Collections.emptyMap() : params;
            ResultSet resultSet = graph.query(cypher, safeParams);
            List<Map<String, Object>> rows = new ArrayList<>();
            for (Record record : resultSet) {
                Map<String, Object> row = new LinkedHashMap<>();
                for (String key : record.keys()) {
                    row.put(key, record.getValue(key));
                }
                rows.add(row);
            }
            return rows;
        } catch (RuntimeException e) {
            throw new GraphUnavailableException(
                    "FalkorDB write query on graph '" + graphName + "' failed: " + e.getMessage(), e);
        }
    }

    /**
     * Read-only connectivity check. An empty graph key still means FalkorDB is reachable.
     * Never issues write queries, so ping cannot create or mutate the graph.
     */
    public boolean ping() {
        if (driver == null) {
            return false;
        }
        try {
            query("RETURN 1 AS ping", Collections.emptyMap());
            return true;
        } catch (GraphUnavailableException e) {
            String msg = e.getMessage() != null ? e.getMessage().toLowerCase() : "";
            return msg.contains("empty key") || msg.contains("graph key not found");
        } catch (Throwable t) {
            return false;
        }
    }
}
