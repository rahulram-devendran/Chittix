package com.companybrain.provider;

import com.companybrain.config.LlmProperties;
import com.companybrain.provider.adapter.AnthropicProviderAdapter;
import com.companybrain.provider.adapter.GeminiProviderAdapter;
import com.companybrain.provider.adapter.OllamaProviderAdapter;
import com.companybrain.provider.adapter.OpenAiCompatibleProviderAdapter;
import com.companybrain.provider.adapter.OpenAiProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelProviderRegistryTest {

    private ModelProviderRegistry registry;
    private LlmProperties properties;

    @BeforeEach
    void setUp() {
        registry = new ModelProviderRegistry(List.of(
                new OllamaProviderAdapter(),
                new OpenAiCompatibleProviderAdapter(),
                new OpenAiProviderAdapter(),
                new GeminiProviderAdapter(),
                new AnthropicProviderAdapter()
        ));
        properties = new LlmProperties();
    }

    @Test
    void registersAllRequiredAdapters() {
        assertEquals(5, registry.getRegisteredProviders().size());
        assertTrue(registry.isRegistered(ModelProvider.OLLAMA));
        assertTrue(registry.isRegistered(ModelProvider.OPENAI_COMPATIBLE));
        assertTrue(registry.isRegistered(ModelProvider.OPENAI));
        assertTrue(registry.isRegistered(ModelProvider.GEMINI));
        assertTrue(registry.isRegistered(ModelProvider.ANTHROPIC));
    }

    @Test
    void retrievesAdapterByProviderEnum() {
        ModelProviderAdapter adapter = registry.getAdapter(ModelProvider.OLLAMA);
        assertNotNull(adapter);
        assertEquals(ModelProvider.OLLAMA, adapter.getProvider());
        assertTrue(adapter.isImplemented());
    }

    @Test
    void verifiesAllAdaptersAreImplemented() {
        assertTrue(registry.getAdapter(ModelProvider.OLLAMA).isImplemented());
        assertTrue(registry.getAdapter(ModelProvider.OPENAI_COMPATIBLE).isImplemented());
        assertTrue(registry.getAdapter(ModelProvider.OPENAI).isImplemented());
        assertTrue(registry.getAdapter(ModelProvider.GEMINI).isImplemented());
        assertTrue(registry.getAdapter(ModelProvider.ANTHROPIC).isImplemented());
    }

    @Test
    void validatesProviderReadinessViaIsConfigured() {
        // Ollama and OpenAI-Compatible are ready with default base URLs
        assertTrue(registry.getAdapter(ModelProvider.OLLAMA).isConfigured(properties));
        assertTrue(registry.getAdapter(ModelProvider.OPENAI_COMPATIBLE).isConfigured(properties));

        // Providers requiring API keys are not ready until keys are supplied
        assertFalse(registry.getAdapter(ModelProvider.OPENAI).isConfigured(properties));
        assertFalse(registry.getAdapter(ModelProvider.GEMINI).isConfigured(properties));
        assertFalse(registry.getAdapter(ModelProvider.ANTHROPIC).isConfigured(properties));

        // When keys are configured, they become ready
        properties.setOpenaiApiKey("sk-test");
        properties.setGeminiApiKey("gm-test");
        properties.setAnthropicApiKey("ant-test");

        assertTrue(registry.getAdapter(ModelProvider.OPENAI).isConfigured(properties));
        assertTrue(registry.getAdapter(ModelProvider.GEMINI).isConfigured(properties));
        assertTrue(registry.getAdapter(ModelProvider.ANTHROPIC).isConfigured(properties));
    }

    @Test
    void returnsEmptyOptionalForUnregisteredOrNullProvider() {
        Optional<ModelProviderAdapter> nullResult = registry.findAdapter(null);
        assertTrue(nullResult.isEmpty());
    }

    @Test
    void throwsExceptionWhenRetrievingMissingProvider() {
        ModelProviderRegistry emptyRegistry = new ModelProviderRegistry(List.of());
        assertThrows(IllegalArgumentException.class, () -> emptyRegistry.getAdapter(ModelProvider.OLLAMA));
    }
}
