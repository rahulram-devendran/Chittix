package com.companybrain.dto;

import java.util.List;

public record AskResponse(
        String question,
        boolean answered,
        String answerType,
        String answer,
        List<String> facts,
        List<DecisionContext> decisions,
        List<Evidence> evidence) {
}
