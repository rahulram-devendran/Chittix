package com.companybrain.dto;

import java.util.List;

public record GraphDataResponse(
        String graph,
        int nodeCount,
        int relationshipCount,
        List<GraphNodeDto> nodes,
        List<GraphRelationshipDto> relationships
) {}
