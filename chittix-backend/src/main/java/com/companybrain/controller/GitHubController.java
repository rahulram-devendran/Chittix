package com.companybrain.controller;

import com.companybrain.dto.github.GitHubConfigRequest;
import com.companybrain.dto.github.GitHubImportRequest;
import com.companybrain.dto.github.GitHubJobStatusResponse;
import com.companybrain.dto.github.GitHubRepoDto;
import com.companybrain.dto.github.GitHubStatusResponse;
import com.companybrain.service.GitHubApiClient;
import com.companybrain.service.GitHubImportService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * Controller exposing endpoints for GitHub connection verification,
 * repository discovery, and metadata import job management.
 */
@RestController
@RequestMapping("/api/github")
public class GitHubController {

    private final GitHubApiClient gitHubApiClient;
    private final GitHubImportService gitHubImportService;

    public GitHubController(GitHubApiClient gitHubApiClient, GitHubImportService gitHubImportService) {
        this.gitHubApiClient = gitHubApiClient;
        this.gitHubImportService = gitHubImportService;
    }

    /**
     * Checks if GitHub token is configured in backend environment and verifies connectivity/scopes.
     */
    @GetMapping("/status")
    public GitHubStatusResponse getStatus() {
        return gitHubApiClient.verifyAccess();
    }

    /**
     * Validates and activates a candidate in-memory GitHub Personal Access Token (PAT).
     * Protected by loopback check for local-demo security.
     */
    @PostMapping("/configure")
    public GitHubStatusResponse configure(@Valid @RequestBody GitHubConfigRequest request,
                                          HttpServletRequest httpRequest) {
        verifyLocalAdminAccess(httpRequest);
        return gitHubApiClient.configureToken(request.token());
    }

    /**
     * Lists repositories accessible to the configured GitHub identity.
     */
    @GetMapping("/repos")
    public List<GitHubRepoDto> listRepositories() {
        return gitHubApiClient.listRepositories();
    }

    /**
     * Enqueues an import job for selected repositories.
     */
    @PostMapping("/import")
    public ResponseEntity<Map<String, String>> startImport(@Valid @RequestBody GitHubImportRequest request) {
        String jobId = gitHubImportService.startImport(request.repositories());
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "QUEUED"
        ));
    }

    /**
     * Retrieves current status and metrics of a specific import job.
     */
    @GetMapping("/jobs/{jobId}")
    public GitHubJobStatusResponse getJobStatus(@PathVariable String jobId) {
        return gitHubImportService.getJobStatus(jobId);
    }

    /**
     * Lists recent import jobs.
     */
    @GetMapping("/jobs")
    public List<GitHubJobStatusResponse> listJobs() {
        return gitHubImportService.listJobs();
    }

    /**
     * Restricts sensitive configuration testing and changes to local single-admin loopback traffic.
     * Documented limitation: This is for local hackathon demo purposes and not production multi-user security.
     */
    private void verifyLocalAdminAccess(HttpServletRequest request) {
        if (request == null) {
            return;
        }
        String remoteAddr = request.getRemoteAddr();
        boolean isLocal = "127.0.0.1".equals(remoteAddr)
                || "0:0:0:0:0:0:0:1".equals(remoteAddr)
                || "::1".equals(remoteAddr)
                || "localhost".equalsIgnoreCase(remoteAddr);

        if (!isLocal) {
            throw new ResponseStatusException(
                    HttpStatus.FORBIDDEN,
                    "Admin configuration operations are restricted to local loopback (single-admin hackathon demo mode)."
            );
        }
    }
}
