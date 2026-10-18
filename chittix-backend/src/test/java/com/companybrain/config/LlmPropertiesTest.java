package com.companybrain.config;

import com.companybrain.provider.ModelProvider;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LlmPropertiesTest {

    @Test
    void applyCandidateStoresCustomGeminiAndAnthropicEndpoints() {
        LlmProperties properties = new LlmProperties();

        properties.applyCandidate("gemini", "gemini-1.5-flash", "https://llm-gateway.example/gemini", "gm-key");
        assertEquals("https://llm-gateway.example/gemini", properties.resolveBaseUrl(ModelProvider.GEMINI));
        assertEquals("gm-key", properties.resolveApiKey(ModelProvider.GEMINI));

        properties.applyCandidate("anthropic", "claude-3-5-haiku-20241022", "https://llm-gateway.example/anthropic", "ant-key");
        assertEquals("https://llm-gateway.example/anthropic", properties.resolveBaseUrl(ModelProvider.ANTHROPIC));
        assertEquals("ant-key", properties.resolveApiKey(ModelProvider.ANTHROPIC));
    }

    @Test
    void applyCandidateDoesNotClearExistingKeyWhenApiKeyBlank() {
        LlmProperties properties = new LlmProperties();
        properties.setGeminiApiKey("existing-gemini");
        properties.setAnthropicApiKey("existing-anthropic");

        properties.applyCandidate("gemini", "gemini-1.5-pro", "https://custom.googleapis.com", "");
        properties.applyCandidate("anthropic", "claude-3-5-sonnet-latest", "https://custom.anthropic.example", "   ");

        assertEquals("existing-gemini", properties.getGeminiApiKey());
        assertEquals("existing-anthropic", properties.getAnthropicApiKey());
        assertEquals("https://custom.googleapis.com", properties.getGeminiBaseUrl());
        assertEquals("https://custom.anthropic.example", properties.getAnthropicBaseUrl());
        assertEquals("gemini-1.5-pro", properties.getGeminiModel());
        assertEquals("claude-3-5-sonnet-latest", properties.getAnthropicModel());
    }

    @Test
    void createCandidateDoesNotMutateActiveGeminiEndpoint() {
        LlmProperties properties = new LlmProperties();
        properties.setGeminiBaseUrl("https://generativelanguage.googleapis.com");

        LlmProperties candidate = properties.createCandidate(
                "gemini",
                "gemini-1.5-flash",
                "https://proxy.example/genai",
                "candidate-key"
        );

        assertEquals("https://proxy.example/genai", candidate.resolveBaseUrl(ModelProvider.GEMINI));
        assertEquals("https://generativelanguage.googleapis.com", properties.resolveBaseUrl(ModelProvider.GEMINI));
        assertEquals("", properties.resolveApiKey(ModelProvider.GEMINI));
    }

    @Test
    void activatingOneProviderDoesNotOverwriteAnotherProvidersModelOrEndpoint() {
        LlmProperties properties = new LlmProperties();
        properties.applyCandidate("ollama", "qwen2.5:1.5b", "http://localhost:11434", null);
        properties.applyCandidate("openai-compatible", "llama3", "http://localhost:8000/v1", "compat-key");
        properties.applyCandidate("openai", "gpt-4o-mini", "https://api.openai.com/v1", "sk-openai");
        properties.applyCandidate("gemini", "gemini-1.5-flash", "https://generativelanguage.googleapis.com", "gm-key");

        assertEquals("gemini", properties.getProvider());
        assertEquals("qwen2.5:1.5b", properties.resolveModel(ModelProvider.OLLAMA));
        assertEquals("http://localhost:11434", properties.resolveBaseUrl(ModelProvider.OLLAMA));
        assertEquals("", properties.resolveApiKey(ModelProvider.OLLAMA));
        assertEquals("llama3", properties.resolveModel(ModelProvider.OPENAI_COMPATIBLE));
        assertEquals("http://localhost:8000/v1", properties.resolveBaseUrl(ModelProvider.OPENAI_COMPATIBLE));
        assertEquals("compat-key", properties.resolveApiKey(ModelProvider.OPENAI_COMPATIBLE));
        assertEquals("gpt-4o-mini", properties.resolveModel(ModelProvider.OPENAI));
        assertEquals("sk-openai", properties.resolveApiKey(ModelProvider.OPENAI));
        assertEquals("gemini-1.5-flash", properties.resolveModel(ModelProvider.GEMINI));
        assertEquals("gm-key", properties.resolveApiKey(ModelProvider.GEMINI));
        assertFalse(properties.hasApiKeyConfigured(ModelProvider.OLLAMA));
        assertTrue(properties.hasApiKeyConfigured(ModelProvider.OPENAI_COMPATIBLE));
    }

    @Test
    void openAiCompatibleDoesNotReuseOfficialOpenAiApiKey() {
        LlmProperties properties = new LlmProperties();
        properties.setOpenaiApiKey("sk-official");
        properties.setApiKey("generic-key");

        assertEquals("", properties.resolveApiKey(ModelProvider.OPENAI_COMPATIBLE));
        assertEquals("sk-official", properties.resolveApiKey(ModelProvider.OPENAI));
    }

    @Test
    void ollamaNeverRequiresOrResolvesAnApiKey() {
        LlmProperties properties = new LlmProperties();
        properties.setApiKey("should-not-apply");
        properties.applyCandidate("ollama", "qwen2.5:1.5b", "http://localhost:11434", "ignored-key");

        assertEquals("", properties.resolveApiKey(ModelProvider.OLLAMA));
        assertFalse(properties.hasApiKeyConfigured(ModelProvider.OLLAMA));
    }
}
