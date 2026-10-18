package com.companybrain.dto;

import java.util.List;

public record AskResponse(
        String question,
        boolean answered,
        String answerType,
        String answer,
        List<String> facts,
        List<DecisionContext> decisions,
        List<Evidence> evidence,
        String modelUsed,
        String providerUsed,
        boolean fallbackUsed,
        String fallbackReason
) {
    public AskResponse(
            String question,
            boolean answered,
            String answerType,
            String answer,
            List<String> facts,
            List<DecisionContext> decisions,
            List<Evidence> evidence
    ) {
        this(question, answered, answerType, answer, facts, decisions, evidence, null, null, "DETERMINISTIC_FALLBACK".equals(answerType), null);
    }
}
