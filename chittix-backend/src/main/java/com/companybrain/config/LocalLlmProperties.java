
package com.companybrain.config;

/**
 * Legacy configuration properties for local LLM inference.
 * Extends {@link LlmProperties} for full backwards compatibility with existing components and tests.
 */
public class LocalLlmProperties extends LlmProperties {

    public LocalLlmProperties() {
        super();
    }

    public LocalLlmProperties(boolean enabled, String baseUrl, String model, int timeoutSeconds, double temperature, int maxTokens) {
        super(enabled, "ollama", model, baseUrl, timeoutSeconds, temperature, maxTokens);
    }
}
