package com.companybrain.service;

import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.source.SourceJobStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceImportServiceTest {

    @Mock
    SourceIntegrationService sourceIntegrationService;

    @Mock
    GraphClient graphClient;

    @Mock
    DocumentIngestionService documentIngestionService;

    ObjectMapper objectMapper = new ObjectMapper();
    SourceImportService importService;

    @BeforeEach
    void setUp() {
        importService = new SourceImportService(sourceIntegrationService, graphClient, documentIngestionService, objectMapper);
    }

    @Test
    void startImportFailsImmediatelyIfFalkorDbOffline() throws Exception {
        when(sourceIntegrationService.isConfigured("gitlab")).thenReturn(true);
        when(graphClient.ping()).thenReturn(false);

        String jobId = importService.startImport("gitlab", List.of("myorg/project"));
        assertNotNull(jobId);

        Thread.sleep(150);

        SourceJobStatusResponse status = importService.getJobStatus(jobId);
        assertEquals("FAILED", status.status());
        assertFalse(status.errors().isEmpty());
        assertTrue(status.errors().get(0).contains("FalkorDB is offline"));
    }

    @Test
    void importGitLabProjectProcessesCommitsAndMergeRequests() throws Exception {
        when(sourceIntegrationService.isConfigured("gitlab")).thenReturn(true);
        when(sourceIntegrationService.getCredential("gitlab")).thenReturn(
                new SourceIntegrationService.StoredCredential("glpat-token", "", "https://gitlab.com")
        );
        when(graphClient.ping()).thenReturn(true);

        HttpResponse<String> projResp = mock(HttpResponse.class);
        when(projResp.statusCode()).thenReturn(200);
        when(projResp.body()).thenReturn("""
                {
                  "id": 99,
                  "name": "payments-api",
                  "path_with_namespace": "fintech/payments-api",
                  "description": "GitLab Payments Microservice",
                  "web_url": "https://gitlab.com/fintech/payments-api",
                  "default_branch": "main",
                  "last_activity_at": "2026-10-18T10:00:00Z"
                }
                """);

        HttpResponse<String> commitsResp = mock(HttpResponse.class);
        when(commitsResp.statusCode()).thenReturn(200);
        when(commitsResp.body()).thenReturn("""
                [
                  {
                    "id": "abc123456789",
                    "short_id": "abc1234",
                    "title": "feat: add webhook handler",
                    "author_name": "Dev Alice",
                    "author_email": "alice@fintech.io",
                    "created_at": "2026-10-18T09:00:00Z",
                    "message": "feat: add webhook handler\\n\\nHandles Stripe events",
                    "web_url": "https://gitlab.com/fintech/payments-api/-/commit/abc1234"
                  }
                ]
                """);

        HttpResponse<String> mrResp = mock(HttpResponse.class);
        when(mrResp.statusCode()).thenReturn(200);
        when(mrResp.body()).thenReturn("""
                [
                  {
                    "iid": 12,
                    "title": "Refactor payment processor",
                    "state": "merged",
                    "author": { "username": "alice" },
                    "created_at": "2026-10-17T08:00:00Z",
                    "merged_at": "2026-10-18T08:30:00Z",
                    "web_url": "https://gitlab.com/fintech/payments-api/-/merge_requests/12",
                    "description": "Cleaned up obsolete database calls"
                  }
                ]
                """);

        when(sourceIntegrationService.executeAuthenticatedGet(eq("gitlab"), contains("/projects/fintech%2Fpayments-api")))
                .thenReturn(projResp);
        when(sourceIntegrationService.executeAuthenticatedGet(eq("gitlab"), contains("/repository/commits")))
                .thenReturn(commitsResp);
        when(sourceIntegrationService.executeAuthenticatedGet(eq("gitlab"), contains("/merge_requests")))
                .thenReturn(mrResp);

        when(documentIngestionService.ingestDocument(anyString(), anyString()))
                .thenReturn(DocumentIngestionResult.success("sourceId", "title", 2));

        String jobId = importService.startImport("gitlab", List.of("fintech/payments-api"));
        assertNotNull(jobId);

        Thread.sleep(300);

        SourceJobStatusResponse status = importService.getJobStatus(jobId);
        assertEquals("COMPLETED", status.status());
        assertEquals(1, status.itemsProcessed());
        assertEquals(1, status.commitsImported());
        assertEquals(1, status.pullRequestsImported());
        assertTrue(status.chunksCreated() >= 2);

        // Verify Repository node persisted in FalkorDB
        verify(graphClient, atLeastOnce()).executeWrite(contains("MERGE (r:Repository {source_id: $sourceId})"), anyMap());
    }

    @Test
    void importConfluenceSpaceProcessesPages() throws Exception {
        when(sourceIntegrationService.isConfigured("confluence")).thenReturn(true);
        when(sourceIntegrationService.getCredential("confluence")).thenReturn(
                new SourceIntegrationService.StoredCredential("api-token", "user@company.com", "https://company.atlassian.net")
        );
        when(graphClient.ping()).thenReturn(true);

        HttpResponse<String> spaceResp = mock(HttpResponse.class);
        when(spaceResp.statusCode()).thenReturn(200);
        when(spaceResp.body()).thenReturn("""
                {
                  "name": "Engineering Architecture",
                  "description": { "plain": { "value": "Core tech docs" } },
                  "_links": { "webui": "/spaces/ENG" }
                }
                """);

        HttpResponse<String> pagesResp = mock(HttpResponse.class);
        when(pagesResp.statusCode()).thenReturn(200);
        when(pagesResp.body()).thenReturn("""
                {
                  "results": [
                    {
                      "id": "1001",
                      "title": "Payment Architecture Guidelines",
                      "body": { "storage": { "value": "<p>Payment service uses PostgreSQL database.</p>" } },
                      "history": { "createdBy": { "displayName": "Chief Architect" } },
                      "version": { "when": "2026-10-18T10:00:00Z" },
                      "_links": { "webui": "/spaces/ENG/pages/1001" }
                    }
                  ]
                }
                """);

        when(sourceIntegrationService.executeAuthenticatedGet(eq("confluence"), contains("/rest/api/space/ENG")))
                .thenReturn(spaceResp);
        when(sourceIntegrationService.executeAuthenticatedGet(eq("confluence"), contains("/rest/api/content")))
                .thenReturn(pagesResp);

        when(documentIngestionService.ingestDocument(anyString(), anyString()))
                .thenReturn(DocumentIngestionResult.success("confluence:page:1001", "Payment Architecture Guidelines", 3));

        String jobId = importService.startImport("confluence", List.of("ENG"));
        assertNotNull(jobId);

        Thread.sleep(300);

        SourceJobStatusResponse status = importService.getJobStatus(jobId);
        assertEquals("COMPLETED", status.status());
        assertEquals(1, status.itemsProcessed());
        assertEquals(1, status.pullRequestsImported()); // pages count

        // Verify Space node persisted in FalkorDB
        verify(graphClient, atLeastOnce()).executeWrite(contains("MERGE (s:Space {source_id: $sourceId})"), anyMap());
    }
}
