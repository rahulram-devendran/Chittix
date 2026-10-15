package com.companybrain.dto;

import java.util.List;

public record PaymentsContextResponse(String graph, int count, List<DecisionContext> contexts) {
}
