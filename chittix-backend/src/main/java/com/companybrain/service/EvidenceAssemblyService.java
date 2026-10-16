package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.RetrievedChunk;
import com.companybrain.dto.RetrievedPassage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * Gathers and correlates verified structural graph context with semantically retrieved
 * document passages without fabricating rationale or calling an LLM.
 */
@Service
public class EvidenceAssemblyService {

    private static final Logger log = LoggerFactory.getLogger(EvidenceAssemblyService.class);
    public static final int DEFAULT_RETRIEVAL_TOP_K = 5;

    private final ContextQueryService contextQueryService;
    private final VectorRetrievalService vectorRetrievalService;

    public EvidenceAssemblyService(ContextQueryService contextQueryService,
                                   VectorRetrievalService vectorRetrievalService) {
        this.contextQueryService = contextQueryService;
        this.vectorRetrievalService = vectorRetrievalService;
    }

    /**
     * Assembles a hybrid evidence package for the question using default retrieval top-K (5).
     *
     * @param question user query (must not be blank)
     * @return structured EvidencePackage containing graph facts, retrieved passages, and limitation notes
     */
    public EvidencePackage assembleEvidence(String question) {
        return assembleEvidence(question, DEFAULT_RETRIEVAL_TOP_K);
    }

    /**
     * Assembles a hybrid evidence package for the question with configurable retrieval top-K.
     *
     * @param question user query (must not be blank)
     * @param topK     maximum number of document passages to retrieve
     * @return structured EvidencePackage containing graph facts, retrieved passages, and limitation notes
     */
    public EvidencePackage assembleEvidence(String question, int topK) {
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("Question must not be blank");
        }
        if (topK <= 0) {
            throw new IllegalArgumentException("topK must be greater than 0, got: " + topK);
        }

        String trimmedQuestion = question.trim();

        // 1. Gather structural graph context
        // Infrastructure exceptions (e.g. GraphUnavailableException) propagate explicitly
        List<DecisionContext> contexts = contextQueryService.findForQuestion(trimmedQuestion);
        List<String> graphFacts = new ArrayList<>();
        if (contexts != null) {
            for (DecisionContext c : contexts) {
                if (c.evidencePath() != null) {
                    graphFacts.addAll(c.evidencePath());
                }
            }
        } else {
            contexts = List.of();
        }

        // 2. Gather semantic document passages
        // Infrastructure/model exceptions propagate explicitly; absent index returns empty list
        List<RetrievedChunk> chunks = vectorRetrievalService.retrieve(trimmedQuestion, topK);
        List<RetrievedPassage> passages = new ArrayList<>();
        if (chunks != null) {
            for (RetrievedChunk c : chunks) {
                RetrievedPassage passage = RetrievedPassage.fromRetrievedChunk(c);
                if (passage != null) {
                    passages.add(passage);
                }
            }
        }

        boolean hasGraph = !graphFacts.isEmpty();
        boolean hasDocs = !passages.isEmpty();
        boolean hasAny = hasGraph || hasDocs;

        // 3. Formulate transparent, factual limitation notes
        List<String> limitations = new ArrayList<>();

        if (!hasAny) {
            limitations.add("No graph facts or document evidence were found in the company knowledge base for this question.");
        } else {
            if (!hasGraph) {
                limitations.add("No structural relationships (decisions, projects, systems) were identified in the company graph.");
            }
            if (!hasDocs) {
                limitations.add("Graph facts establish decision existence and affected systems, but no written rationale or documentation text was retrieved.");
            }
        }

        limitations.add("Passages represent factual text excerpts from verified documents with preserved source identifiers; no unconfirmed rationale or external metadata has been fabricated.");

        log.debug("Assembled evidence for '{}': {} graph facts, {} passages (hasGraph={}, hasDocs={})",
                trimmedQuestion, graphFacts.size(), passages.size(), hasGraph, hasDocs);

        return new EvidencePackage(
                trimmedQuestion,
                List.copyOf(graphFacts),
                List.copyOf(contexts),
                List.copyOf(passages),
                hasGraph,
                hasDocs,
                hasAny,
                List.copyOf(limitations)
        );
    }
}
