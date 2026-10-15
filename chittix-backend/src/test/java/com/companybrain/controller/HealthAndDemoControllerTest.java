package com.companybrain.controller;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.HealthResponse;
import com.companybrain.exception.GlobalExceptionHandler;
import com.companybrain.service.ContextQueryService;
import com.companybrain.service.GraphClient;
import com.companybrain.service.HealthService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class HealthAndDemoControllerTest {

    @Mock
    HealthService healthService;
    @Mock
    ContextQueryService contextQueryService;
    @Mock
    GraphClient graphClient;

    @Test
    void healthIs200WhenDatabaseUp() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse("UP", "UP", "g", "ok"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();

        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.application").value("UP"))
                .andExpect(jsonPath("$.falkordb").value("UP"));
    }

    @Test
    void healthIs503WhenDatabaseDown() throws Exception {
        when(healthService.check()).thenReturn(new HealthResponse("UP", "DOWN", "g", "down"));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService)).build();

        mvc.perform(get("/api/health"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.application").value("UP"))
                .andExpect(jsonPath("$.falkordb").value("DOWN"));
    }

    @Test
    void paymentsContextReturnsGraphResults() throws Exception {
        when(graphClient.graphName()).thenReturn("g");
        when(contextQueryService.findPaymentsContext()).thenReturn(List.of(
                new DecisionContext("D", "P", "S", null, "Doc", "SRC", List.of("path"))));
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoController(contextQueryService, graphClient))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/demo/payments-context"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1))
                .andExpect(jsonPath("$.contexts[0].sourceId").value("SRC"));
    }

    @Test
    void paymentsContextIs404WhenGraphHasNoMatch() throws Exception {
        when(graphClient.graphName()).thenReturn("g");
        when(contextQueryService.findPaymentsContext()).thenReturn(List.of());
        MockMvc mvc = MockMvcBuilders.standaloneSetup(new DemoController(contextQueryService, graphClient))
                .setControllerAdvice(new GlobalExceptionHandler()).build();

        mvc.perform(get("/api/demo/payments-context")).andExpect(status().isNotFound());
    }
}
