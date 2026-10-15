package com.companybrain.service;

import com.companybrain.dto.HealthResponse;
import org.springframework.stereotype.Service;

@Service
public class HealthService {

    private final GraphClient graphClient;

    public HealthService(GraphClient graphClient) {
        this.graphClient = graphClient;
    }

    public HealthResponse check() {
        boolean up = graphClient.ping();
        return new HealthResponse(
                "UP",
                up ? "UP" : "DOWN",
                graphClient.graphName(),
                up ? "FalkorDB reachable" : "FalkorDB not reachable or graph query failed");
    }
}
