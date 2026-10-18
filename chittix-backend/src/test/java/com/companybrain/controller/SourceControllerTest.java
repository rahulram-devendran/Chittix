package com.companybrain.controller;

import com.companybrain.dto.source.SourceItemDto;
import com.companybrain.dto.source.SourceJobStatusResponse;
import com.companybrain.dto.source.SourceStatusResponse;
import com.companybrain.exception.GlobalExceptionHandler;
import com.companybrain.service.SourceImportService;
import com.companybrain.service.SourceIntegrationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class SourceControllerTest {

    @Mock
    SourceIntegrationService sourceIntegrationService;

    @Mock
    SourceImportService sourceImportService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new SourceController(sourceIntegrationService, sourceImportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getAllStatusesReturnsMap() throws Exception {
        when(sourceIntegrationService.getAllSourceStatuses()).thenReturn(Map.of(
                "gitlab", new SourceStatusResponse("gitlab", true, true, "gitlab-user", "GitLab User", "https://gitlab.com", "Connected", true, "Active")
        ));

        mvc.perform(get("/api/sources/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.gitlab.connected").value(true))
                .andExpect(jsonPath("$.gitlab.username").value("gitlab-user"));
    }

    @Test
    void getStatusReturnsProviderStatus() throws Exception {
        when(sourceIntegrationService.getStatus("confluence")).thenReturn(
                new SourceStatusResponse("confluence", true, true, "conf-user", "Confluence User", "https://corp.atlassian.net", "Connected", true, "Active")
        );

        mvc.perform(get("/api/sources/status/confluence"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.provider").value("confluence"))
                .andExpect(jsonPath("$.connected").value(true));
    }

    @Test
    void configureRejectsNonLoopback() throws Exception {
        mvc.perform(post("/api/sources/configure")
                        .with(request -> {
                            request.setRemoteAddr("192.168.1.100");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"gitlab\",\"token\":\"glpat-secret\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void configureAcceptsLoopback() throws Exception {
        when(sourceIntegrationService.configureSource(any())).thenReturn(
                new SourceStatusResponse("gitlab", true, true, "user1", "User One", "https://gitlab.com", "Configured", true, "Active")
        );

        mvc.perform(post("/api/sources/configure")
                        .with(request -> {
                            request.setRemoteAddr("127.0.0.1");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"gitlab\",\"token\":\"glpat-secret\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.username").value("user1"));
    }

    @Test
    void listItemsReturnsDiscoveredItems() throws Exception {
        when(sourceIntegrationService.listItems("gitlab")).thenReturn(List.of(
                new SourceItemDto("101", "payment-service", "backend/payment-service", "Payment processing repo", "https://gitlab.com/backend/payment-service", "main", "project", false, Map.of())
        ));

        mvc.perform(get("/api/sources/gitlab/items"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("101"))
                .andExpect(jsonPath("$[0].name").value("payment-service"));
    }

    @Test
    void startImportQueuesJob() throws Exception {
        when(sourceImportService.startImport(eq("bitbucket"), any())).thenReturn("job-bb-456");

        mvc.perform(post("/api/sources/bitbucket/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"items\":[\"proj/repo\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-bb-456"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void getJobStatusReturnsDetails() throws Exception {
        when(sourceImportService.getJobStatus("job-bb-456")).thenReturn(
                new SourceJobStatusResponse("job-bb-456", "bitbucket", "IN_PROGRESS", 1, 1, 15, 2, 30, "repo", "Importing commits...", 1000L, null, List.of("Started"), List.of(), List.of("repo"))
        );

        mvc.perform(get("/api/sources/jobs/job-bb-456"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-bb-456"))
                .andExpect(jsonPath("$.status").value("IN_PROGRESS"))
                .andExpect(jsonPath("$.commitsImported").value(15));
    }

    @Test
    void listJobsReturnsHistory() throws Exception {
        when(sourceImportService.listJobs("confluence")).thenReturn(List.of(
                new SourceJobStatusResponse("job-conf-1", "confluence", "COMPLETED", 1, 1, 0, 0, 20, "space", "Done", 1000L, 2000L, List.of("Finished"), List.of(), List.of("space"))
        ));

        mvc.perform(get("/api/sources/confluence/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].jobId").value("job-conf-1"))
                .andExpect(jsonPath("$[0].chunksCreated").value(20));
    }
}
