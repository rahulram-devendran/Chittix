package com.companybrain.provider;

import java.util.Arrays;
import java.util.Collections;
import java.util.Set;

/**
 * Enumeration of supported LLM provider identities and their declared capabilities.
 */
public enum ModelProvider {

    OLLAMA("ollama", Set.of(
            ModelCapability.CHAT,
            ModelCapability.LOCAL,
            ModelCapability.STREAMING
    )),

    OPENAI_COMPATIBLE("openai-compatible", Set.of(
            ModelCapability.CHAT,
            ModelCapability.STREAMING
    )),

    OPENAI("openai", Set.of(
            ModelCapability.CHAT,
            ModelCapability.STREAMING,
            ModelCapability.STRUCTURED_OUTPUT,
            ModelCapability.TOOL_CALLING
    )),

    GEMINI("gemini", Set.of(
            ModelCapability.CHAT,
            ModelCapability.STREAMING,
            ModelCapability.MULTIMODAL,
            ModelCapability.STRUCTURED_OUTPUT
    )),

    ANTHROPIC("anthropic", Set.of(
            ModelCapability.CHAT,
            ModelCapability.STREAMING,
            ModelCapability.TOOL_CALLING
    ));

    private final String id;
    private final Set<ModelCapability> capabilities;

    ModelProvider(String id, Set<ModelCapability> capabilities) {
        this.id = id;
        this.capabilities = Collections.unmodifiableSet(capabilities);
    }

    public String getId() {
        return id;
    }

    public Set<ModelCapability> getCapabilities() {
        return capabilities;
    }

    public boolean supports(ModelCapability capability) {
        return capabilities.contains(capability);
    }

    /**
     * Resolves a provider string into a ModelProvider instance.
     * Tolerates variations like "openai_compatible", "openai-compatible", "OPENAI", etc.
     *
     * @param raw input provider string
     * @return matching ModelProvider
     * @throws IllegalArgumentException if provider is unrecognized or blank
     */
    public static ModelProvider fromString(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Model provider name must not be blank");
        }

        String normalized = raw.trim().toLowerCase().replace('_', '-');
        for (ModelProvider provider : values()) {
            if (provider.id.equalsIgnoreCase(normalized) || provider.name().equalsIgnoreCase(raw.trim())) {
                return provider;
            }
        }

        String valid = Arrays.toString(Arrays.stream(values()).map(ModelProvider::getId).toArray());
        throw new IllegalArgumentException("Unsupported model provider: '" + raw + "'. Supported providers are: " + valid);
    }
}
