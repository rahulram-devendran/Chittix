package com.companybrain.provider;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.GeneratedAnswer;

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
}
