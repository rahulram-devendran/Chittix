package com.companybrain.controller;

import com.companybrain.dto.AskRequest;
import com.companybrain.dto.AskResponse;
import com.companybrain.dto.ProviderStatusResponse;
import com.companybrain.service.BrainService;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brain")
public class BrainController {

    private final BrainService brainService;

    public BrainController(BrainService brainService) {
        this.brainService = brainService;
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return brainService.ask(request.question());
    }

    @GetMapping("/provider-status")
    public ProviderStatusResponse getProviderStatus() {
        return brainService.getProviderStatus();
    }
}
