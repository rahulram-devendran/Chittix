package com.companybrain.controller;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.PaymentsContextResponse;
import com.companybrain.exception.NotFoundException;
import com.companybrain.service.ContextQueryService;
import com.companybrain.service.GraphClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/demo")
public class DemoController {

    private final ContextQueryService contextQueryService;
    private final GraphClient graphClient;

    public DemoController(ContextQueryService contextQueryService, GraphClient graphClient) {
        this.contextQueryService = contextQueryService;
        this.graphClient = graphClient;
    }

    @GetMapping("/payments-context")
    public PaymentsContextResponse paymentsContext() {
        List<DecisionContext> contexts = contextQueryService.findPaymentsContext();
        if (contexts.isEmpty()) {
            throw new NotFoundException("No Decision/Project/System/Document chain found for the payments "
                    + "project in graph '" + graphClient.graphName() + "'.");
        }
        return new PaymentsContextResponse(graphClient.graphName(), contexts.size(), contexts);
    }

    @GetMapping("/graph")
    public com.companybrain.dto.GraphDataResponse graph() {
        return contextQueryService.getKnowledgeGraph();
    }
}
