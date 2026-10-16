package com.companybrain.service;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.RetrievedPassage;
import com.companybrain.provider.ModelCapability;
import com.companybrain.provider.ModelProvider;
import com.companybrain.provider.ModelProviderAdapter;
import com.companybrain.provider.ModelProviderRegistry;
import com.companybrain.provider.adapter.AnthropicProviderAdapter;
import com.companybrain.provider.adapter.GeminiProviderAdapter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MultiProviderAnswerGenerationServiceTest {

    @Mock
    private ModelProviderAdapter mockOllamaAdapter;

    private LlmProperties properties;
    private ModelProviderRegistry registry;
    private MultiProviderAnswerGenerationService orchestrator;

    private final RetrievedPassage realPassage = new RetrievedPassage(
            "DOC-001#chunk-0",
            0,
            "PostgreSQL was chosen for Payment Service because of ACID guarantees.",
            "DOC-001",
            "Payment Architecture Design",
            0.15,
            0.85
    );

    private final DecisionContext decisionContext = new DecisionContext(
            "Use PostgreSQL for payments",
            "Payment Service",
            "PostgreSQL",
            "Payments",
            "Payment Architecture Design",
            "DOC-001",
            List.of("(Decision)-[AFFECTS]->(Project)")
    );

    @BeforeEach
    void setUp() {
        properties = new LlmProperties(true, "ollama", "qwen2.5:1.5b", "http://localhost:11434", 15, 0.1, 500);

        lenient().when(mockOllamaAdapter.getProvider()).thenReturn(ModelProvider.OLLAMA);
        lenient().when(mockOllamaAdapter.isImplemented()).thenReturn(true);
        lenient().when(mockOllamaAdapter.isConfigured(any())).thenReturn(true);

        registry = new ModelProviderRegistry(List.of(
                mockOllamaAdapter,
                new GeminiProviderAdapter(),
                new AnthropicProviderAdapter()
        ));

        orchestrator = new MultiProviderAnswerGenerationService(properties, registry);
    }

    @Test
    void rejectsNullEvidencePackage() {
        assertThrows(IllegalArgumentException.class, () -> orchestrator.generateAnswer(null));
    }

    @Test
    void returnsInsufficientEvidenceWithoutCallingAnyProviderWhenNoEvidenceAvailable() {
        EvidencePackage emptyEvidence = new EvidencePackage(
                "What is our vacation policy?",
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false,
                List.of("No evidence found")
        );

        GeneratedAnswer answer = orchestrator.generateAnswer(emptyEvidence);

        assertEquals("INSUFFICIENT_EVIDENCE", answer.answerType());
        assertEquals(EvidenceStatus.INSUFFICIENT, answer.evidenceStatus());
        assertFalse(answer.fallbackUsed());
        verify(mockOllamaAdapter, never()).generateAnswer(any(), any());
    }

    @Test
    void activatesDeterministicFallbackImmediatelyWhenLlmIsDisabledByConfig() {
        properties.setEnabled(false);

        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(decisionContext),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        GeneratedAnswer answer = orchestrator.generateAnswer(evidence);

        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertTrue(answer.fallbackReason().contains("disabled"));
        verify(mockOllamaAdapter, never()).generateAnswer(any(), any());
    }

    @Test
    void handlesInvalidProviderNameWithDescriptiveFallbackListingSupportedProviders() {
        properties.setProvider("unsupported-vendor-ai");

        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(decisionContext),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        GeneratedAnswer answer = orchestrator.generateAnswer(evidence);

        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertTrue(answer.fallbackReason().contains("Unsupported model provider: 'unsupported-vendor-ai'"));
        assertTrue(answer.fallbackReason().contains("ollama"));
        verify(mockOllamaAdapter, never()).generateAnswer(any(), any());
    }

    @Test
    void reportsUnconfiguredProviderWhenApiKeyMissing() {
        properties.setProvider("gemini");
        properties.setGeminiApiKey("");
        properties.setApiKey("");

        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(decisionContext),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        GeneratedAnswer answer = orchestrator.generateAnswer(evidence);

        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertTrue(answer.fallbackReason().contains("not properly configured"));
        verify(mockOllamaAdapter, never()).generateAnswer(any(), any());
    }

    @Test
    void reportsUnimplementedProviderExplicitlyWhenAdapterIsNotImplemented() {
        ModelProviderAdapter mockUnimplemented = new ModelProviderAdapter() {
            @Override
            public ModelProvider getProvider() {
                return ModelProvider.ANTHROPIC;
            }
            @Override
            public Set<ModelCapability> getSupportedCapabilities() {
                return Set.of();
            }
            @Override
            public boolean isImplemented() {
                return false;
            }
            @Override
            public boolean isConfigured(LlmProperties properties) {
                return true;
            }
            @Override
            public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
                return null;
            }
        };

        ModelProviderRegistry customRegistry = new ModelProviderRegistry(List.of(mockUnimplemented));
        MultiProviderAnswerGenerationService customOrchestrator =
                new MultiProviderAnswerGenerationService(properties, customRegistry);

        properties.setProvider("anthropic");

        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(decisionContext),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        GeneratedAnswer answer = customOrchestrator.generateAnswer(evidence);

        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertTrue(answer.fallbackReason().contains("adapter is not implemented"));
    }

    @Test
    void delegatesToConfiguredOllamaProviderWhenValid() {
        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(decisionContext),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        GeneratedAnswer expectedAnswer = new GeneratedAnswer(
                "Why PostgreSQL?",
                "Grounded answer from Ollama [DOC-001]",
                "LLM_GROUNDED",
                List.of("fact-1"),
                List.of(realPassage),
                evidence.distinctSources(),
                EvidenceStatus.FULL_EVIDENCE,
                List.of(),
                false,
                null
        );

        when(mockOllamaAdapter.generateAnswer(eq(evidence), eq(properties))).thenReturn(expectedAnswer);

        GeneratedAnswer answer = orchestrator.generateAnswer(evidence);

        assertEquals("LLM_GROUNDED", answer.answerType());
        assertEquals("Grounded answer from Ollama [DOC-001]", answer.answerText());
        assertFalse(answer.fallbackUsed());
        verify(mockOllamaAdapter).generateAnswer(eq(evidence), eq(properties));
    }
}
