package com.companybrain.service;

import com.companybrain.config.GitHubProperties;
import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.github.GitHubCommitDto;
import com.companybrain.dto.github.GitHubJobStatusResponse;
import com.companybrain.dto.github.GitHubPullRequestDto;
import com.companybrain.dto.github.GitHubRepoDto;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubImportServiceTest {

    @Mock
    GitHubApiClient gitHubApiClient;

    @Mock
    GraphClient graphClient;

    @Mock
    DocumentIngestionService documentIngestionService;

    GitHubProperties properties;
    GitHubImportService importService;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setToken("ghp_test");
        properties.setCommitLimit(10);
        properties.setPrLimit(10);

        importService = new GitHubImportService(gitHubApiClient, properties, graphClient, documentIngestionService);
    }

    @Test
    void startImportFailsImmediatelyIfFalkorDbOffline() throws Exception {
        when(graphClient.ping()).thenReturn(false);

        String jobId = importService.startImport(List.of("acme/payments"));
        assertNotNull(jobId);

        // Wait a short moment for virtual thread to execute
        Thread.sleep(150);

        GitHubJobStatusResponse status = importService.getJobStatus(jobId);
        assertEquals("FAILED", status.status());
        assertFalse(status.errors().isEmpty());
        assertTrue(status.errors().get(0).contains("FalkorDB is offline"));
    }

    @Test
    void startImportProcessesRepositoryCommitsAndPrs() throws Exception {
        when(graphClient.ping()).thenReturn(true);

        GitHubRepoDto repoDto = new GitHubRepoDto(
                1L, "payments", "acme/payments", "Payment processing microservice",
                "https://github.com/acme/payments", "main", false, 15, 2, 1, "2026-10-17T00:00:00Z"
        );
        when(gitHubApiClient.fetchRepository("acme", "payments")).thenReturn(repoDto);

        GitHubCommitDto commit = new GitHubCommitDto(
                "c123456789", "feat: integrate stripe gateway", "Developer Dave",
                "dave@acme.com", "2026-10-16T10:00:00Z", "https://github.com/acme/payments/commit/c123"
        );
        when(gitHubApiClient.fetchCommits("acme", "payments", 10)).thenReturn(List.of(commit));

        GitHubPullRequestDto pr = new GitHubPullRequestDto(
                101, "Stripe API v3 migration", "Updates API version and tests", "closed",
                "dave", "2026-10-15T09:00:00Z", "2026-10-16T12:00:00Z", "2026-10-16T12:00:00Z",
                "https://github.com/acme/payments/pull/101", "lead-reviewer (APPROVED)"
        );
        when(gitHubApiClient.fetchPullRequests("acme", "payments", 10)).thenReturn(List.of(pr));

        when(documentIngestionService.ingestDocument(anyString(), anyString()))
                .thenReturn(DocumentIngestionResult.success("sourceId", "title", 2));

        String jobId = importService.startImport(List.of("acme/payments"));
        assertNotNull(jobId);

        // Wait a short moment for virtual thread to finish
        Thread.sleep(300);

        GitHubJobStatusResponse status = importService.getJobStatus(jobId);
        assertEquals("COMPLETED", status.status());
        assertEquals(1, status.repositoriesProcessed());
        assertEquals(1, status.commitsImported());
        assertEquals(1, status.pullRequestsImported());
        assertEquals(6, status.chunksCreated()); // 2 chunks per doc x 3 docs (repo overview + 1 commit + 1 PR)

        verify(documentIngestionService, atLeastOnce()).registerDocument(eq("github:repo:acme/payments"), anyString());
        verify(documentIngestionService, atLeastOnce()).registerDocument(eq("github:commit:acme/payments@c123456"), anyString());
        verify(documentIngestionService, atLeastOnce()).registerDocument(eq("github:pr:acme/payments#101"), anyString());
        verify(graphClient, atLeastOnce()).executeWrite(anyString(), anyMap());
    }

    private void assertFalse(boolean condition) {
        org.junit.jupiter.api.Assertions.assertFalse(condition);
    }
}
