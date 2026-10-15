package com.companybrain.service;

import com.companybrain.dto.AskResponse;
import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.Evidence;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class BrainService {

    static final String PRELIMINARY = "PRELIMINARY";
    static final String INSUFFICIENT =
            "Insufficient evidence found in the company graph to answer this question.";

    private final ContextQueryService contextQueryService;

    public BrainService(ContextQueryService contextQueryService) {
        this.contextQueryService = contextQueryService;
    }

    public AskResponse ask(String question) {
        List<DecisionContext> contexts = contextQueryService.findForQuestion(question.trim());
        if (contexts.isEmpty()) {
            return new AskResponse(question, false, PRELIMINARY, INSUFFICIENT,
                    List.of(), List.of(), List.of());
        }

        List<String> facts = new ArrayList<>();
        List<String> sentences = new ArrayList<>();
        for (DecisionContext c : contexts) {
            facts.addAll(c.evidencePath());
            sentences.add("The graph records the decision \"" + c.decision() + "\" affecting the "
                    + c.project() + " project, which uses " + c.system()
                    + ". It is supported by the document \"" + c.documentTitle()
                    + "\" (source " + c.sourceId() + ").");
        }
        String answer = "Preliminary answer (assembled from graph facts, no LLM used): "
                + String.join(" ", sentences)
                + " The graph holds no written rationale beyond this supporting document.";

        List<Evidence> evidence = contexts.stream()
                .map(c -> new Evidence(c.documentTitle(), c.sourceId()))
                .distinct()
                .toList();

        return new AskResponse(question, true, PRELIMINARY, answer, facts, contexts, evidence);
    }
}
