package com.companybrain.service;

import com.companybrain.dto.AskResponse;
import com.companybrain.dto.DecisionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BrainServiceTest {

    @Mock
    ContextQueryService contextQueryService;

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
}
