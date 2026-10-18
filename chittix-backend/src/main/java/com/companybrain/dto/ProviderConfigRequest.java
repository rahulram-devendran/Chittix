package com.companybrain.dto;

import jakarta.validation.constraints.NotBlank;

/**
 * Request DTO for testing and updating AI provider configuration at runtime.
 * Never logs or exposes API keys.
 */
public record ProviderConfigRequest(
        @NotBlank(message = "Provider identifier is required")
        String provider,
        String model,
        String baseUrl,
        String apiKey
) {
    /**
     * Safe string representation ensuring apiKey is never written to logs or console.
     */
    @Override
    public String toString() {
        return "ProviderConfigRequest[" +
                "provider='" + provider + '\'' +
                ", model='" + model + '\'' +
                ", baseUrl='" + baseUrl + '\'' +
                ", apiKey=" + (apiKey != null && !apiKey.isBlank() ? "[PROTECTED]" : "[EMPTY]") +
                ']';
    }
}
