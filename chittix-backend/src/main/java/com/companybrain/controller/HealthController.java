package com.companybrain.controller;

import com.companybrain.dto.HealthResponse;
import com.companybrain.service.HealthService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class HealthController {

    private final HealthService healthService;

    public HealthController(HealthService healthService) {
        this.healthService = healthService;
    }

    /** 200 when FalkorDB is reachable, 503 (same JSON body) when it is not. */
    @GetMapping("/health")
    public ResponseEntity<HealthResponse> health() {
        HealthResponse body = healthService.check();
        HttpStatus status = "UP".equals(body.falkordb()) ? HttpStatus.OK : HttpStatus.SERVICE_UNAVAILABLE;
        return ResponseEntity.status(status).body(body);
    }
}
