package com.companybrain.provider;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.GeneratedAnswer;

import com.companybrain.dto.ProviderTestResult;

import java.util.Set;

/**
 * Adapter interface for interacting with a specific LLM vendor runtime.
 * Provides capability introspection, implementation status, configuration readiness,
 * and grounded answer generation.
 */
public interface ModelProviderAdapter {

    /**
     * Unique identifier for this provider.
     */
    ModelProvider getProvider();

    /**
     * Set of capabilities supported by this provider adapter.
     */
    Set<ModelCapability> getSupportedCapabilities();

    /**
     * Returns true if a working network/runtime client is implemented for this provider.
     * When false, the orchestrator explicitly reports unconfigured/unsupported status.
     */
    boolean isImplemented();

    /**
     * Returns true if the required settings and credentials (e.g. API keys, URLs)
     * are present for this provider to execute successfully.
     */
    boolean isConfigured(LlmProperties properties);

    /**
     * Generates a grounded answer from the verified evidence package using this provider.
     *
     * @param evidence verified graph context and document passages
     * @param properties configuration properties for timeouts, models, and credentials
     * @return grounded GeneratedAnswer or deterministic fallback
     */
    GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties);

    /**
     * Tests connectivity to the provider endpoint with the given candidate properties.
     * Validates credentials and endpoint reachability without mutating active configuration.
     *
     * @param properties candidate properties to test
     * @return ProviderTestResult with status, latency, and safe message
     */
    default ProviderTestResult testConnection(LlmProperties properties) {
        return new ProviderTestResult(false, getProvider().getId(), properties.resolveModel(getProvider()),
                "Connection testing is not supported for provider: " + getProvider().getId(), null);
    }
}
