package com.companybrain.provider;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModelProviderTest {

    @Test
    void declaresRequiredProviders() {
        assertNotNull(ModelProvider.OLLAMA);
        assertNotNull(ModelProvider.OPENAI_COMPATIBLE);
        assertNotNull(ModelProvider.OPENAI);
        assertNotNull(ModelProvider.GEMINI);
        assertNotNull(ModelProvider.ANTHROPIC);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ollama", "OLLAMA", "Ollama", " ollama "})
    void parsesOllamaVariations(String raw) {
        assertEquals(ModelProvider.OLLAMA, ModelProvider.fromString(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai-compatible", "OPENAI-COMPATIBLE", "openai_compatible", "OPENAI_COMPATIBLE"})
    void parsesOpenAiCompatibleVariations(String raw) {
        assertEquals(ModelProvider.OPENAI_COMPATIBLE, ModelProvider.fromString(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"openai", "OPENAI", "OpenAI"})
    void parsesOpenAiVariations(String raw) {
        assertEquals(ModelProvider.OPENAI, ModelProvider.fromString(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"gemini", "GEMINI", "Gemini"})
    void parsesGeminiVariations(String raw) {
        assertEquals(ModelProvider.GEMINI, ModelProvider.fromString(raw));
    }

    @ParameterizedTest
    @ValueSource(strings = {"anthropic", "ANTHROPIC", "Anthropic"})
    void parsesAnthropicVariations(String raw) {
        assertEquals(ModelProvider.ANTHROPIC, ModelProvider.fromString(raw));
    }

    @Test
    void rejectsNullOrBlankProvider() {
        assertThrows(IllegalArgumentException.class, () -> ModelProvider.fromString(null));
        assertThrows(IllegalArgumentException.class, () -> ModelProvider.fromString("   "));
    }

    @Test
    void rejectsUnsupportedProviderWithHelpfulMessage() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> ModelProvider.fromString("unknown-ai"));
        assertTrue(ex.getMessage().contains("Unsupported model provider: 'unknown-ai'"));
        assertTrue(ex.getMessage().contains("ollama"));
        assertTrue(ex.getMessage().contains("openai-compatible"));
    }

    @Test
    void verifiesExplicitCapabilities() {
        assertTrue(ModelProvider.OLLAMA.supports(ModelCapability.LOCAL));
        assertTrue(ModelProvider.OLLAMA.supports(ModelCapability.CHAT));
        assertFalse(ModelProvider.OLLAMA.supports(ModelCapability.MULTIMODAL));

        assertTrue(ModelProvider.OPENAI.supports(ModelCapability.STRUCTURED_OUTPUT));
        assertTrue(ModelProvider.OPENAI.supports(ModelCapability.TOOL_CALLING));
        assertFalse(ModelProvider.OPENAI.supports(ModelCapability.LOCAL));

        assertTrue(ModelProvider.GEMINI.supports(ModelCapability.MULTIMODAL));
    }
}
