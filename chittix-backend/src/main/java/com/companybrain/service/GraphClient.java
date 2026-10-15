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
            ResultSet resultSet = graph.readOnlyQuery(cypher, params);
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

    /** Real connectivity check: runs a query against the configured graph. */
    public boolean ping() {
        try {
            query("MATCH (n) RETURN count(n) AS nodes", Collections.emptyMap());
            return true;
        } catch (GraphUnavailableException e) {
            return false;
        }
    }
}
