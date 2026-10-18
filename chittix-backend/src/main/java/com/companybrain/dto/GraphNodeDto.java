package com.companybrain.dto;

import java.util.Map;

public record GraphNodeDto(
        String id,
        String name,
        String type,
        String sourceId,
        Map<String, String> properties
) {}
