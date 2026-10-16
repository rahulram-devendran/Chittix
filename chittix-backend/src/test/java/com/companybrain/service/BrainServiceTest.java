package com.companybrain.service;

import com.companybrain.dto.AskResponse;
import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.Evidence;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrainServiceTest {

    @Mock
    ContextQueryService contextQueryService;

    @Mock
    EvidenceAssemblyService evidenceAssemblyService;

    @Mock
    AnswerGenerationService answerGenerationService;

    // --- Legacy Constructor Backwards Compatibility Tests ---

    @Test
    void assemblesPreliminaryAnswerFromRetrievedFacts() {
        DecisionContext ctx = new DecisionContext("D", "P", "S", "T", "Doc", "SRC-9",
                List.of("step-1", "step-2"));
        when(contextQueryService.findForQuestion("why S for P?")).thenReturn(List.of(ctx));

        AskResponse response = new BrainService(contextQueryService).ask("why S for P?");

        assertTrue(response.answered());
        assertEquals("PRELIMINARY", response.answerType());
        assertEquals("why S for P?", response.question());
        assertEquals(List.of("step-1", "step-2"), response.facts());
        assertEquals("SRC-9", response.evidence().get(0).sourceId());
        assertTrue(response.answer().startsWith("Preliminary answer"));
        assertTrue(response.answer().contains("SRC-9"));
    }

    @Test
    void reportsInsufficientEvidenceWhenGraphReturnsNothing() {
        when(contextQueryService.findForQuestion("what is the weather?")).thenReturn(List.of());

        AskResponse response = new BrainService(contextQueryService).ask("what is the weather?");

        assertFalse(response.answered());
        assertTrue(response.answer().contains("Insufficient evidence"));
        assertTrue(response.facts().isEmpty());
        assertTrue(response.evidence().isEmpty());
    }

    // --- Primary Hybrid Pipeline Tests ---

    @Test
    void delegatesToEvidenceAssemblyAndAnswerGenerationInPrimaryPipeline() {
        String question = "Why did we choose PostgreSQL?";
        EvidencePackage evidencePackage = new EvidencePackage(
                question,
                List.of("fact-1"),
                List.of(),
                List.of(),
                true,
                false,
                true,
                List.of()
        );
        when(evidenceAssemblyService.assembleEvidence(question)).thenReturn(evidencePackage);

        GeneratedAnswer generated = new GeneratedAnswer(
                question,
                "Grounded answer text",
                "LLM_GROUNDED",
                List.of("fact-1"),
                List.of(),
                List.of(new Evidence("Doc Title", "DOC-001")),
                EvidenceStatus.GRAPH_ONLY,
                List.of(),
                false,
                null
        );
        when(answerGenerationService.generateAnswer(evidencePackage)).thenReturn(generated);

        BrainService brainService = new BrainService(evidenceAssemblyService, answerGenerationService);
        AskResponse response = brainService.ask(question);

        assertTrue(response.answered());
        assertEquals("LLM_GROUNDED", response.answerType());
        assertEquals("Grounded answer text", response.answer());
        assertEquals(List.of("fact-1"), response.facts());
        assertEquals("DOC-001", response.evidence().get(0).sourceId());
    }

    @Test
    void reportsInsufficientEvidenceWhenAssemblyFindsNoEvidence() {
        String question = "What is the policy?";
        EvidencePackage emptyEvidence = new EvidencePackage(
                question,
                List.of(),
                List.of(),
                List.of(),
                false,
                false,
                false,
                List.of("No evidence found")
        );
        when(evidenceAssemblyService.assembleEvidence(question)).thenReturn(emptyEvidence);

        BrainService brainService = new BrainService(evidenceAssemblyService, answerGenerationService);
        AskResponse response = brainService.ask(question);

        assertFalse(response.answered());
        assertEquals("INSUFFICIENT_EVIDENCE", response.answerType());
        assertTrue(response.facts().isEmpty());
    }

    @Test
    void rejectsBlankOrNullQuestion() {
        BrainService brainService = new BrainService(evidenceAssemblyService, answerGenerationService);
        assertThrows(IllegalArgumentException.class, () -> brainService.ask(null));
        assertThrows(IllegalArgumentException.class, () -> brainService.ask("   "));
    }

    @Test
    void returnsProviderStatusWithoutExposingSensitiveKeys() {
        com.companybrain.config.LlmProperties properties = new com.companybrain.config.LlmProperties();
        properties.setProvider("ollama");
        properties.setModel("qwen2.5:1.5b");
        properties.setOpenaiApiKey("sk-secret-do-not-reveal");

        com.companybrain.provider.ModelProviderRegistry registry = new com.companybrain.provider.ModelProviderRegistry(List.of(
                new com.companybrain.provider.adapter.OllamaProviderAdapter(),
                new com.companybrain.provider.adapter.OpenAiProviderAdapter()
        ));

        BrainService brainService = new BrainService(evidenceAssemblyService, answerGenerationService, properties, registry);
        com.companybrain.dto.ProviderStatusResponse status = brainService.getProviderStatus();

        assertEquals("ollama", status.selectedProvider());
        assertEquals("qwen2.5:1.5b", status.configuredModel());
        assertTrue(status.enabled());
        assertTrue(status.implemented());
        assertTrue(status.configured());
        assertFalse(status.availableProviders().isEmpty());
    }
}
