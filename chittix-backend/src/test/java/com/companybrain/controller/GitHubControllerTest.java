package com.companybrain.controller;

import com.companybrain.dto.github.GitHubJobStatusResponse;
import com.companybrain.dto.github.GitHubRepoDto;
import com.companybrain.dto.github.GitHubStatusResponse;
import com.companybrain.exception.GlobalExceptionHandler;
import com.companybrain.service.GitHubApiClient;
import com.companybrain.service.GitHubImportService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class GitHubControllerTest {

    @Mock
    GitHubApiClient gitHubApiClient;

    @Mock
    GitHubImportService gitHubImportService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new GitHubController(gitHubApiClient, gitHubImportService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void getStatusReturnsConfiguredStatus() throws Exception {
        when(gitHubApiClient.verifyAccess()).thenReturn(new GitHubStatusResponse(
                true, "octocat", "The Octocat", "https://avatar.url",
                List.of("repo", "read:org"), 5000, 4990, 1700000000L, "Verified"
        ));

        mvc.perform(get("/api/github/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.username").value("octocat"))
                .andExpect(jsonPath("$.rateLimitRemaining").value(4990));
    }

    @Test
    void listReposReturnsRepositories() throws Exception {
        when(gitHubApiClient.listRepositories()).thenReturn(List.of(
                new GitHubRepoDto(123L, "chittix", "myorg/chittix", "AI Knowledge Platform",
                        "https://github.com/myorg/chittix", "main", false, 42, 5, 2, "2026-10-17T00:00:00Z")
        ));

        mvc.perform(get("/api/github/repos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("chittix"))
                .andExpect(jsonPath("$[0].fullName").value("myorg/chittix"));
    }

    @Test
    void startImportEnqueuesJob() throws Exception {
        when(gitHubImportService.startImport(anyList())).thenReturn("job-12345678");

        mvc.perform(post("/api/github/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositories\":[\"myorg/chittix\"]}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").value("job-12345678"))
                .andExpect(jsonPath("$.status").value("QUEUED"));
    }

    @Test
    void startImportRejectsEmptyRepositories() throws Exception {
        mvc.perform(post("/api/github/import")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"repositories\":[]}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getJobStatusReturnsDetails() throws Exception {
        when(gitHubImportService.getJobStatus("job-12345678")).thenReturn(new GitHubJobStatusResponse(
                "job-12345678", "RUNNING", 1, 0, 5, 2, 8,
                "myorg/chittix", "Importing Commits", 1000L, null,
                List.of("Log entry 1"), List.of(), List.of()
        ));

        mvc.perform(get("/api/github/jobs/job-12345678"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value("job-12345678"))
                .andExpect(jsonPath("$.status").value("RUNNING"))
                .andExpect(jsonPath("$.commitsImported").value(5));
    }

    @Test
    void configureTokenSucceedsOnValidToken() throws Exception {
        when(gitHubApiClient.configureToken("ghp_valid_token")).thenReturn(new GitHubStatusResponse(
                true, "octocat", "The Octocat", "https://avatar.url",
                List.of("repo"), 5000, 4990, 1700000000L, "GitHub connection verified successfully"
        ));

        mvc.perform(post("/api/github/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ghp_valid_token\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.username").value("octocat"));
    }

    @Test
    void configureTokenFailsOnInvalidToken() throws Exception {
        when(gitHubApiClient.configureToken("ghp_invalid_token"))
                .thenThrow(new org.springframework.web.server.ResponseStatusException(
                        org.springframework.http.HttpStatus.BAD_REQUEST,
                        "GitHub token validation failed: GitHub token authentication failed (HTTP 401 Unauthorized)"
                ));

        mvc.perform(post("/api/github/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ghp_invalid_token\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("GitHub token validation failed")));
    }

    @Test
    void configureTokenRejectsNonLoopbackAccess() throws Exception {
        mvc.perform(post("/api/github/configure")
                        .with(request -> {
                            request.setRemoteAddr("198.51.100.12");
                            return request;
                        })
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"ghp_valid_token\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("restricted to local loopback")));
    }

    @Test
    void configureTokenRejectsBlankToken() throws Exception {
        mvc.perform(post("/api/github/configure")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"token\":\"   \"}"))
                .andExpect(status().isBadRequest());
    }
}
