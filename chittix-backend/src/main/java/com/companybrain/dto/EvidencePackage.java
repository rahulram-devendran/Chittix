package com.companybrain.dto;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Typed evidence container combining verified graph relationships with semantically
 * retrieved document passages. Designed for deterministic grounding before LLM answer generation.
 *
 * @param question            the original user query
 * @param graphFacts          discrete verified relationship facts from the company graph
 * @param decisionContexts    rich decision and project context paths
 * @param retrievedPassages   verified document excerpts with real provenance
 * @param hasGraphContext     true if one or more graph facts were found
 * @param hasDocumentEvidence true if one or more document passages were retrieved
 * @param hasAnyEvidence      true if either graph facts or document passages are present
 * @param limitations         explicit notes regarding missing evidence, score bounds, or boundaries
 */
public record EvidencePackage(
        String question,
        List<String> graphFacts,
        List<DecisionContext> decisionContexts,
        List<RetrievedPassage> retrievedPassages,
        boolean hasGraphContext,
        boolean hasDocumentEvidence,
        boolean hasAnyEvidence,
        List<String> limitations
) {
    public EvidencePackage {
        if (graphFacts == null) graphFacts = List.of();
        if (decisionContexts == null) decisionContexts = List.of();
        if (retrievedPassages == null) retrievedPassages = List.of();
        if (limitations == null) limitations = List.of();
    }

    /**
     * Extracts distinct document sources present in either graph relationships or retrieved passages.
     */
    public List<Evidence> distinctSources() {
        List<Evidence> sources = new ArrayList<>();
        for (DecisionContext ctx : decisionContexts) {
            if (ctx.documentTitle() != null && ctx.sourceId() != null) {
                sources.add(new Evidence(ctx.documentTitle(), ctx.sourceId()));
            }
        }
        for (RetrievedPassage p : retrievedPassages) {
            if (p.documentTitle() != null && !p.documentTitle().isBlank()
                    && p.sourceId() != null && !p.sourceId().isBlank()) {
                sources.add(new Evidence(p.documentTitle(), p.sourceId()));
            }
        }
        return sources.stream().distinct().toList();
    }
}
