package com.companybrain.controller;

import com.companybrain.dto.source.SourceConfigRequest;
import com.companybrain.dto.source.SourceImportRequest;
import com.companybrain.dto.source.SourceItemDto;
import com.companybrain.dto.source.SourceJobStatusResponse;
import com.companybrain.dto.source.SourceStatusResponse;
import com.companybrain.service.SourceImportService;
import com.companybrain.service.SourceIntegrationService;
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
 * Controller exposing endpoints for testing, configuring, discovering, and importing
 * data from external sources (GitLab, Bitbucket, Confluence) with loopback admin restrictions.
 */
@RestController
@RequestMapping("/api/sources")
public class SourceController {

    private final SourceIntegrationService sourceIntegrationService;
    private final SourceImportService sourceImportService;

    public SourceController(SourceIntegrationService sourceIntegrationService,
                            SourceImportService sourceImportService) {
        this.sourceIntegrationService = sourceIntegrationService;
        this.sourceImportService = sourceImportService;
    }

    @GetMapping("/status")
    public Map<String, SourceStatusResponse> getAllStatuses() {
        return sourceIntegrationService.getAllSourceStatuses();
    }

    @GetMapping("/status/{provider}")
    public SourceStatusResponse getStatus(@PathVariable String provider) {
        return sourceIntegrationService.getStatus(provider);
    }

    @PostMapping("/configure")
    public SourceStatusResponse configure(@Valid @RequestBody SourceConfigRequest request,
                                          HttpServletRequest httpRequest) {
        verifyLocalAdminAccess(httpRequest);
        return sourceIntegrationService.configureSource(request);
    }

    @PostMapping("/test")
    public SourceStatusResponse testConnection(@Valid @RequestBody SourceConfigRequest request,
                                               HttpServletRequest httpRequest) {
        verifyLocalAdminAccess(httpRequest);
        return sourceIntegrationService.testConnection(
                request.provider(),
                request.token(),
                request.username(),
                request.baseUrl()
        );
    }

    @GetMapping("/{provider}/items")
    public List<SourceItemDto> listItems(@PathVariable String provider) {
        return sourceIntegrationService.listItems(provider);
    }

    @PostMapping("/{provider}/import")
    public ResponseEntity<Map<String, String>> startImport(@PathVariable String provider,
                                                          @Valid @RequestBody SourceImportRequest request) {
        String jobId = sourceImportService.startImport(provider, request.items());
        return ResponseEntity.accepted().body(Map.of(
                "jobId", jobId,
                "status", "QUEUED"
        ));
    }

    @GetMapping("/jobs/{jobId}")
    public SourceJobStatusResponse getJobStatus(@PathVariable String jobId) {
        return sourceImportService.getJobStatus(jobId);
    }

    @GetMapping("/{provider}/jobs")
    public List<SourceJobStatusResponse> listJobs(@PathVariable String provider) {
        return sourceImportService.listJobs(provider);
    }

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
