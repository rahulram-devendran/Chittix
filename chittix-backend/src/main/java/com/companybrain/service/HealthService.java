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
        boolean up = false;
        try {
            up = graphClient != null && graphClient.ping();
        } catch (Exception e) {
            up = false;
        }
        String graphName = graphClient != null ? graphClient.graphName() : "company_brain";
        return new HealthResponse(
                "UP",
                up ? "UP" : "DOWN",
                graphName,
                up ? "FalkorDB reachable" : "FalkorDB not reachable or graph query failed");
    }
}
