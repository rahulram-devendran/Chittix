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
     * Resolves the appropriate model name for the target provider.
     * Uses provider-specific defaults if the generic model wasn't explicitly changed from the Ollama default.
     */
    public String resolveModel(ModelProvider targetProvider) {
        if (targetProvider == null) {
            return model;
        }

        // If the model was customized from default "qwen2.5:1.5b", prioritize it
        if (model != null && !model.isBlank() && !model.equals("qwen2.5:1.5b")) {
            return model;
        }

        return switch (targetProvider) {
            case OPENAI -> (openaiModel != null && !openaiModel.isBlank()) ? openaiModel : "gpt-4o-mini";
            case GEMINI -> (geminiModel != null && !geminiModel.isBlank()) ? geminiModel : "gemini-1.5-flash";
            case ANTHROPIC -> (anthropicModel != null && !anthropicModel.isBlank()) ? anthropicModel : "claude-3-5-haiku-20241022";
            case OPENAI_COMPATIBLE -> (openaiModel != null && !openaiModel.isBlank()) ? openaiModel : model;
            case OLLAMA -> model;
        };
    }

    /**
     * Resolves the appropriate API key for the given provider.
     * Never commits or hardcodes secrets.
     */
    public String resolveApiKey(ModelProvider targetProvider) {
        if (targetProvider == null) {
            return apiKey != null ? apiKey : "";
        }
        return switch (targetProvider) {
            case OPENAI -> (openaiApiKey != null && !openaiApiKey.isBlank()) ? openaiApiKey : apiKey;
            case OPENAI_COMPATIBLE -> (openaiApiKey != null && !openaiApiKey.isBlank()) ? openaiApiKey : apiKey;
            case GEMINI -> (geminiApiKey != null && !geminiApiKey.isBlank()) ? geminiApiKey : apiKey;
            case ANTHROPIC -> (anthropicApiKey != null && !anthropicApiKey.isBlank()) ? anthropicApiKey : apiKey;
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
            case OLLAMA -> (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "http://localhost:11434";
            case OPENAI -> (openaiBaseUrl != null && !openaiBaseUrl.isBlank()) ? openaiBaseUrl : "https://api.openai.com/v1";
            case OPENAI_COMPATIBLE -> (baseUrl != null && !baseUrl.isBlank()) ? baseUrl : "http://localhost:8000/v1";
            case GEMINI -> (geminiBaseUrl != null && !geminiBaseUrl.isBlank()) ? geminiBaseUrl : "https://generativelanguage.googleapis.com";
            case ANTHROPIC -> (anthropicBaseUrl != null && !anthropicBaseUrl.isBlank()) ? anthropicBaseUrl : "https://api.anthropic.com";
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
}
