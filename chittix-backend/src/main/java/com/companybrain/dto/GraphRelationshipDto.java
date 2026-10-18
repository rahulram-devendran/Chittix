package com.companybrain.dto;

public record GraphRelationshipDto(
        String id,
        String source,
        String target,
        String type
) {}
