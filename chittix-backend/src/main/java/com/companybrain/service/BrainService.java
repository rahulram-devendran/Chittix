package com.companybrain.service;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.AskResponse;
import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.Evidence;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.ProviderStatusResponse;
import com.companybrain.provider.ModelProvider;
import com.companybrain.provider.ModelProviderAdapter;
import com.companybrain.provider.ModelProviderRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class BrainService {

    static final String PRELIMINARY = "PRELIMINARY";
    static final String INSUFFICIENT =
            "Insufficient evidence found in the company graph to answer this question.";

    private final ContextQueryService contextQueryService;
    private final EvidenceAssemblyService evidenceAssemblyService;
    private final AnswerGenerationService answerGenerationService;
    private final LlmProperties llmProperties;
    private final ModelProviderRegistry modelProviderRegistry;

    /**
     * Primary constructor autowired by Spring Boot.
     */
    @Autowired
    public BrainService(EvidenceAssemblyService evidenceAssemblyService,
                        AnswerGenerationService answerGenerationService,
                        @Autowired(required = false) LlmProperties llmProperties,
                        @Autowired(required = false) ModelProviderRegistry modelProviderRegistry) {
        this.contextQueryService = null;
        this.evidenceAssemblyService = evidenceAssemblyService;
        this.answerGenerationService = answerGenerationService;
        this.llmProperties = llmProperties;
        this.modelProviderRegistry = modelProviderRegistry;
    }

    /**
     * Two-arg constructor preserved for unit tests.
     */
    public BrainService(EvidenceAssemblyService evidenceAssemblyService,
                        AnswerGenerationService answerGenerationService) {
        this(evidenceAssemblyService, answerGenerationService, null, null);
    }

    /**
     * Legacy constructor preserved for backwards compatibility with existing unit tests.
     */
    public BrainService(ContextQueryService contextQueryService) {
        this.contextQueryService = contextQueryService;
        this.evidenceAssemblyService = null;
        this.answerGenerationService = null;
        this.llmProperties = null;
        this.modelProviderRegistry = null;
    }

    public AskResponse ask(String question) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }

        // 1. If evidence assembly and answer generation are wired (standard Spring Boot runtime)
        if (evidenceAssemblyService != null && answerGenerationService != null) {
            EvidencePackage evidence = evidenceAssemblyService.assembleEvidence(question.trim());

            if (!evidence.hasAnyEvidence()) {
                return new AskResponse(question, false, "INSUFFICIENT_EVIDENCE", INSUFFICIENT,
                        List.of(), List.of(), List.of());
            }

            GeneratedAnswer generated = answerGenerationService.generateAnswer(evidence);
            boolean answered = generated.evidenceStatus() != EvidenceStatus.INSUFFICIENT;

            return new AskResponse(
                    question,
                    answered,
                    generated.answerType(),
                    generated.answerText(),
                    generated.graphFacts(),
                    evidence.decisionContexts(),
                    generated.distinctSources()
            );
        }

        // 2. Legacy fallback path (when invoked with legacy ContextQueryService constructor)
        List<DecisionContext> contexts = contextQueryService.findForQuestion(question.trim());
        if (contexts == null || contexts.isEmpty()) {
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

    public ProviderStatusResponse getProviderStatus() {
        if (llmProperties == null || modelProviderRegistry == null) {
            return new ProviderStatusResponse(
                    "unconfigured", "none", false, false, false, "", Set.of(), List.of()
            );
        }

        ModelProvider selected;
        try {
            selected = llmProperties.getModelProvider();
        } catch (Exception e) {
            return new ProviderStatusResponse(
                    llmProperties.getProvider(), "unknown", llmProperties.isEnabled(), false, false, "", Set.of(), List.of()
            );
        }

        Optional<ModelProviderAdapter> adapterOpt = modelProviderRegistry.findAdapter(selected);

        boolean implemented = adapterOpt.map(ModelProviderAdapter::isImplemented).orElse(false);
        boolean configured = adapterOpt.map(a -> a.isConfigured(llmProperties)).orElse(false);
        Set<String> capabilities = adapterOpt
                .map(a -> a.getSupportedCapabilities().stream().map(Enum::name).collect(Collectors.toSet()))
                .orElse(Set.of());

        List<ProviderStatusResponse.ProviderInfo> available = modelProviderRegistry.getRegisteredProviders().stream()
                .map(p -> {
                    ModelProviderAdapter a = modelProviderRegistry.getAdapter(p);
                    Set<String> caps = a.getSupportedCapabilities().stream().map(Enum::name).collect(Collectors.toSet());
                    return new ProviderStatusResponse.ProviderInfo(
                            p.getId(),
                            a.isImplemented(),
                            a.isConfigured(llmProperties),
                            caps
                    );
                })
                .toList();

        return new ProviderStatusResponse(
                selected.getId(),
                llmProperties.resolveModel(selected),
                llmProperties.isEnabled(),
                implemented,
                configured,
                llmProperties.resolveBaseUrl(selected),
                capabilities,
                available
        );
    }
}
