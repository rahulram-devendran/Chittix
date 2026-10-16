package com.companybrain.dto;

import java.util.List;
import java.util.Set;

/**
 * DTO detailing the current LLM provider configuration, readiness status,
 * and supported capabilities without revealing any secrets or API keys.
 */
public record ProviderStatusResponse(
        String selectedProvider,
        String configuredModel,
        boolean enabled,
        boolean implemented,
        boolean configured,
        String baseUrl,
        Set<String> supportedCapabilities,
        List<ProviderInfo> availableProviders
) {
    public record ProviderInfo(
            String provider,
            boolean implemented,
            boolean configured,
            Set<String> supportedCapabilities
    ) {}
}
