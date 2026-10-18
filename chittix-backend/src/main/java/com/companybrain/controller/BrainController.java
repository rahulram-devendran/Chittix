package com.companybrain.controller;

import com.companybrain.dto.AskRequest;
import com.companybrain.dto.AskResponse;
import com.companybrain.dto.GraphDataResponse;
import com.companybrain.dto.ProviderConfigRequest;
import com.companybrain.dto.ProviderStatusResponse;
import com.companybrain.dto.ProviderTestResult;
import com.companybrain.service.BrainService;
import com.companybrain.service.ContextQueryService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/brain")
public class BrainController {

    private final BrainService brainService;
    private final ContextQueryService contextQueryService;

    @Autowired
    public BrainController(BrainService brainService, ContextQueryService contextQueryService) {
        this.brainService = brainService;
        this.contextQueryService = contextQueryService;
    }

    public BrainController(BrainService brainService) {
        this(brainService, null);
    }

    @PostMapping("/ask")
    public AskResponse ask(@Valid @RequestBody AskRequest request) {
        return brainService.ask(request.question());
    }

    @GetMapping("/graph")
    public GraphDataResponse getKnowledgeGraph() {
        if (contextQueryService != null) {
            return contextQueryService.getKnowledgeGraph();
        }
        return new GraphDataResponse("company_brain", 0, 0, java.util.List.of(), java.util.List.of());
    }

    @GetMapping("/provider-status")
    public ProviderStatusResponse getProviderStatus() {
        return brainService.getProviderStatus();
    }

    @PostMapping("/test-provider")
    public ProviderTestResult testProvider(@Valid @RequestBody ProviderConfigRequest request,
                                           HttpServletRequest httpRequest) {
        return brainService.testProvider(request, httpRequest);
    }

    @PostMapping("/configure-provider")
    public ProviderStatusResponse configureProvider(@Valid @RequestBody ProviderConfigRequest request,
                                                   HttpServletRequest httpRequest) {
        return brainService.configureProvider(request, httpRequest);
    }
}
