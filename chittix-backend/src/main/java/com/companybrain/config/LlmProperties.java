package com.companybrain.config;

import com.companybrain.provider.ModelProvider;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Externalized configuration properties for LLM providers and models.
 * Supports provider selection (e.g. ollama, openai, openai-compatible, gemini, anthropic),
 * model names, timeouts, generation parameters, and provider-specific credentials.
 */
@Component
@ConfigurationProperties(prefix = "chittix.llm")
public class LlmProperties {

    /**
     * Whether LLM answer generation is enabled globally. Defaults to true.
     */
    private boolean enabled = true;

    /**
     * Selected provider identity ('ollama', 'openai-compatible', 'openai', 'gemini', 'anthropic').
     */
    private String provider = "ollama";

    /**
     * Global or active model name. Defaults to 'qwen2.5:1.5b' for Ollama.
     */
    private String model = "qwen2.5:1.5b";

    /**
     * Base HTTP URL of the active LLM runtime.
     */
    private String baseUrl = "http://localhost:11434";

    /**
     * Request timeout in seconds. Defaults to 15s.
     */
    private int timeoutSeconds = 15;

    /**
     * Temperature for factual generation (0.0 to 1.0).
     */
    private double temperature = 0.1;

    /**
     * Maximum number of tokens to predict.
     */
    private int maxTokens = 500;

    /**
     * Generic API key fallback.
     */
    private String apiKey = "";

    /**
     * Provider-specific credentials, endpoints, and default models.
     */
    private String openaiApiKey = "";
    private String openaiBaseUrl = "https://api.openai.com/v1";
    private String openaiModel = "gpt-4o-mini";

    private String geminiApiKey = "";
    private String geminiBaseUrl = "https://generativelanguage.googleapis.com";
    private String geminiModel = "gemini-1.5-flash";

    private String anthropicApiKey = "";
    private String anthropicBaseUrl = "https://api.anthropic.com";
    private String anthropicModel = "claude-3-5-haiku-20241022";

    private String openaiCompatibleApiKey = "";
    private String openaiCompatibleBaseUrl = "http://localhost:8000/v1";
    private String openaiCompatibleModel = "gpt-4o-mini";

    public LlmProperties() {
    }

    public LlmProperties(boolean enabled, String provider, String model, String baseUrl, int timeoutSeconds, double temperature, int maxTokens) {
        this.enabled = enabled;
        this.provider = provider;
        this.model = model;
        this.baseUrl = baseUrl;
        this.timeoutSeconds = timeoutSeconds;
        this.temperature = temperature;
        this.maxTokens = maxTokens;
    }

    public ModelProvider getModelProvider() {
        return ModelProvider.fromString(this.provider);
    }

    /**
     * Resolves the appropriate model name for the target provider without leaking
     * another provider's active model identifier.
     */
    public String resolveModel(ModelProvider targetProvider) {
        if (targetProvider == null) {
            return firstNonBlank(model, "qwen2.5:1.5b");
        }

        return switch (targetProvider) {
            case OLLAMA -> firstNonBlank(model, "qwen2.5:1.5b");
            case OPENAI -> firstNonBlank(openaiModel, "gpt-4o-mini");
            case OPENAI_COMPATIBLE -> resolveOpenAiCompatibleModel();
            case GEMINI -> firstNonBlank(geminiModel, "gemini-1.5-flash");
            case ANTHROPIC -> firstNonBlank(anthropicModel, "claude-3-5-haiku-20241022");
        };
    }

    /**
     * Resolves the appropriate API key for the given provider.
     * Never commits or hardcodes secrets. Ollama never requires a key.
     */
    public String resolveApiKey(ModelProvider targetProvider) {
        if (targetProvider == null) {
            return apiKey != null ? apiKey : "";
        }
        return switch (targetProvider) {
            case OPENAI -> firstNonBlank(openaiApiKey, apiKey);
            case OPENAI_COMPATIBLE -> resolveOpenAiCompatibleApiKey();
            case GEMINI -> firstNonBlank(geminiApiKey, apiKey);
            case ANTHROPIC -> firstNonBlank(anthropicApiKey, apiKey);
            case OLLAMA -> "";
        };
    }

    /**
     * Resolves the target base URL for the given provider.
     */
    public String resolveBaseUrl(ModelProvider targetProvider) {
        if (targetProvider == null) {
            return baseUrl != null ? baseUrl : "";
        }
        return switch (targetProvider) {
            case OLLAMA -> firstNonBlank(baseUrl, "http://localhost:11434");
            case OPENAI -> firstNonBlank(openaiBaseUrl, "https://api.openai.com/v1");
            case OPENAI_COMPATIBLE -> resolveOpenAiCompatibleBaseUrl();
            case GEMINI -> firstNonBlank(geminiBaseUrl, "https://generativelanguage.googleapis.com");
            case ANTHROPIC -> firstNonBlank(anthropicBaseUrl, "https://api.anthropic.com");
        };
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public String getModel() {
        return model;
    }

    public void setModel(String model) {
        this.model = model;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public int getTimeoutSeconds() {
        return timeoutSeconds;
    }

    public void setTimeoutSeconds(int timeoutSeconds) {
        this.timeoutSeconds = timeoutSeconds;
    }

    public double getTemperature() {
        return temperature;
    }

    public void setTemperature(double temperature) {
        this.temperature = temperature;
    }

    public int getMaxTokens() {
        return maxTokens;
    }

    public void setMaxTokens(int maxTokens) {
        this.maxTokens = maxTokens;
    }

    public String getApiKey() {
        return apiKey;
    }

    public void setApiKey(String apiKey) {
        this.apiKey = apiKey;
    }

    public String getOpenaiApiKey() {
        return openaiApiKey;
    }

    public void setOpenaiApiKey(String openaiApiKey) {
        this.openaiApiKey = openaiApiKey;
    }

    public String getOpenaiBaseUrl() {
        return openaiBaseUrl;
    }

    public void setOpenaiBaseUrl(String openaiBaseUrl) {
        this.openaiBaseUrl = openaiBaseUrl;
    }

    public String getOpenaiModel() {
        return openaiModel;
    }

    public void setOpenaiModel(String openaiModel) {
        this.openaiModel = openaiModel;
    }

    public String getGeminiApiKey() {
        return geminiApiKey;
    }

    public void setGeminiApiKey(String geminiApiKey) {
        this.geminiApiKey = geminiApiKey;
    }

    public String getGeminiBaseUrl() {
        return geminiBaseUrl;
    }

    public void setGeminiBaseUrl(String geminiBaseUrl) {
        this.geminiBaseUrl = geminiBaseUrl;
    }

    public String getGeminiModel() {
        return geminiModel;
    }

    public void setGeminiModel(String geminiModel) {
        this.geminiModel = geminiModel;
    }

    public String getAnthropicApiKey() {
        return anthropicApiKey;
    }

    public void setAnthropicApiKey(String anthropicApiKey) {
        this.anthropicApiKey = anthropicApiKey;
    }

    public String getAnthropicBaseUrl() {
        return anthropicBaseUrl;
    }

    public void setAnthropicBaseUrl(String anthropicBaseUrl) {
        this.anthropicBaseUrl = anthropicBaseUrl;
    }

    public String getAnthropicModel() {
        return anthropicModel;
    }

    public void setAnthropicModel(String anthropicModel) {
        this.anthropicModel = anthropicModel;
    }

    public String getOpenaiCompatibleApiKey() {
        return openaiCompatibleApiKey;
    }

    public void setOpenaiCompatibleApiKey(String openaiCompatibleApiKey) {
        this.openaiCompatibleApiKey = openaiCompatibleApiKey;
    }

    public String getOpenaiCompatibleBaseUrl() {
        return openaiCompatibleBaseUrl;
    }

    public void setOpenaiCompatibleBaseUrl(String openaiCompatibleBaseUrl) {
        this.openaiCompatibleBaseUrl = openaiCompatibleBaseUrl;
    }

    public String getOpenaiCompatibleModel() {
        return openaiCompatibleModel;
    }

    public void setOpenaiCompatibleModel(String openaiCompatibleModel) {
        this.openaiCompatibleModel = openaiCompatibleModel;
    }

    /**
     * Creates an isolated candidate copy of these properties for connection testing.
     * Prevents candidate modifications from mutating this singleton instance.
     */
    public LlmProperties createCandidate(String candidateProvider, String candidateModel, String candidateBaseUrl, String candidateApiKey) {
        LlmProperties copy = new LlmProperties(
                this.enabled,
                this.provider,
                this.model,
                this.baseUrl,
                this.timeoutSeconds,
                this.temperature,
                this.maxTokens
        );
        copy.setApiKey(this.apiKey);
        copy.setOpenaiApiKey(this.openaiApiKey);
        copy.setOpenaiBaseUrl(this.openaiBaseUrl);
        copy.setOpenaiModel(this.openaiModel);
        copy.setGeminiApiKey(this.geminiApiKey);
        copy.setGeminiBaseUrl(this.geminiBaseUrl);
        copy.setGeminiModel(this.geminiModel);
        copy.setAnthropicApiKey(this.anthropicApiKey);
        copy.setAnthropicBaseUrl(this.anthropicBaseUrl);
        copy.setAnthropicModel(this.anthropicModel);
        copy.setOpenaiCompatibleApiKey(this.openaiCompatibleApiKey);
        copy.setOpenaiCompatibleBaseUrl(this.openaiCompatibleBaseUrl);
        copy.setOpenaiCompatibleModel(this.openaiCompatibleModel);

        copy.applyCandidate(candidateProvider, candidateModel, candidateBaseUrl, candidateApiKey);
        return copy;
    }

    /**
     * Safely applies the candidate configuration to this singleton in memory.
     * Note: This is an in-memory runtime update for the local demo session and is not persisted to disk.
     * Provider-specific model, endpoint, and key fields stay isolated so activating one provider
     * does not overwrite another provider's stored values. Blank API keys keep the key already on file.
     */
    public synchronized void applyCandidate(String candidateProvider, String candidateModel, String candidateBaseUrl, String candidateApiKey) {
        if (candidateProvider == null || candidateProvider.isBlank()) {
            return;
        }

        ModelProvider targetProvider = ModelProvider.fromString(candidateProvider);
        this.provider = targetProvider.getId();

        switch (targetProvider) {
            case OLLAMA -> {
                if (hasText(candidateModel)) {
                    this.model = candidateModel.trim();
                }
                if (hasText(candidateBaseUrl)) {
                    this.baseUrl = candidateBaseUrl.trim();
                }
            }
            case OPENAI -> {
                if (hasText(candidateModel)) {
                    this.openaiModel = candidateModel.trim();
                }
                if (hasText(candidateBaseUrl)) {
                    this.openaiBaseUrl = candidateBaseUrl.trim();
                }
                if (hasText(candidateApiKey)) {
                    this.openaiApiKey = candidateApiKey.trim();
                }
            }
            case OPENAI_COMPATIBLE -> {
                if (hasText(candidateModel)) {
                    this.openaiCompatibleModel = candidateModel.trim();
                }
                if (hasText(candidateBaseUrl)) {
                    this.openaiCompatibleBaseUrl = candidateBaseUrl.trim();
                }
                if (hasText(candidateApiKey)) {
                    this.openaiCompatibleApiKey = candidateApiKey.trim();
                }
            }
            case GEMINI -> {
                if (hasText(candidateModel)) {
                    this.geminiModel = candidateModel.trim();
                }
                if (hasText(candidateBaseUrl)) {
                    this.geminiBaseUrl = candidateBaseUrl.trim();
                }
                if (hasText(candidateApiKey)) {
                    this.geminiApiKey = candidateApiKey.trim();
                }
            }
            case ANTHROPIC -> {
                if (hasText(candidateModel)) {
                    this.anthropicModel = candidateModel.trim();
                }
                if (hasText(candidateBaseUrl)) {
                    this.anthropicBaseUrl = candidateBaseUrl.trim();
                }
                if (hasText(candidateApiKey)) {
                    this.anthropicApiKey = candidateApiKey.trim();
                }
            }
        }
    }

    public boolean hasApiKeyConfigured(ModelProvider targetProvider) {
        String resolved = resolveApiKey(targetProvider);
        return resolved != null && !resolved.isBlank();
    }

    private String resolveOpenAiCompatibleModel() {
        if (hasText(openaiCompatibleModel) && !"gpt-4o-mini".equals(openaiCompatibleModel)) {
            return openaiCompatibleModel;
        }
        if ("openai-compatible".equals(provider) && hasText(model) && !"qwen2.5:1.5b".equals(model)) {
            return model;
        }
        return firstNonBlank(openaiCompatibleModel, "gpt-4o-mini");
    }

    private String resolveOpenAiCompatibleBaseUrl() {
        if (hasText(openaiCompatibleBaseUrl) && !"http://localhost:8000/v1".equals(trimTrailingSlash(openaiCompatibleBaseUrl))) {
            return openaiCompatibleBaseUrl;
        }
        if ("openai-compatible".equals(provider) && hasText(baseUrl) && !"http://localhost:11434".equals(trimTrailingSlash(baseUrl))) {
            return baseUrl;
        }
        return firstNonBlank(openaiCompatibleBaseUrl, "http://localhost:8000/v1");
    }

    private String resolveOpenAiCompatibleApiKey() {
        if (hasText(openaiCompatibleApiKey)) {
            return openaiCompatibleApiKey;
        }
        if ("openai-compatible".equals(provider) && hasText(apiKey)) {
            return apiKey;
        }
        return "";
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private static String firstNonBlank(String... values) {
        if (values == null) {
            return "";
        }
        for (String value : values) {
            if (hasText(value)) {
                return value;
            }
        }
        return "";
    }

    private static String trimTrailingSlash(String value) {
        return value == null ? "" : value.replaceAll("/+$", "");
    }
}
