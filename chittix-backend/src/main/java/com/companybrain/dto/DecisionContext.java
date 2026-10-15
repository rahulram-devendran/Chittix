package com.companybrain.dto;

import java.util.List;

/** One Decision -> Project -> System chain plus its supporting document, as read from the graph. */
public record DecisionContext(
        String decision,
        String project,
        String system,
        String ownerTeam,
        String documentTitle,
        String sourceId,
        List<String> evidencePath) {
}
