package com.companybrain.service;

import com.companybrain.dto.HealthResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthServiceTest {

    @Mock
    GraphClient graphClient;

    @Test
    void reportsFalkorDbUpWhenPingSucceeds() {
        when(graphClient.ping()).thenReturn(true);
        when(graphClient.graphName()).thenReturn("company_brain");

        HealthResponse response = new HealthService(graphClient).check();

        assertEquals("UP", response.application());
        assertEquals("UP", response.falkordb());
        assertEquals("company_brain", response.graph());
        assertEquals("FalkorDB reachable", response.message());
    }

    @Test
    void reportsFalkorDbDownWhenPingFails() {
        when(graphClient.ping()).thenReturn(false);
        when(graphClient.graphName()).thenReturn("company_brain");

        HealthResponse response = new HealthService(graphClient).check();

        assertEquals("UP", response.application());
        assertEquals("DOWN", response.falkordb());
        assertEquals("FalkorDB not reachable or graph query failed", response.message());
    }
}
