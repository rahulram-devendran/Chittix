package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.Evidence;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.RetrievedChunk;
import com.companybrain.dto.RetrievedPassage;
import com.companybrain.exception.GraphUnavailableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EvidenceAssemblyServiceTest {

    @Mock
    ContextQueryService contextQueryService;

    @Mock
    VectorRetrievalService vectorRetrievalService;

    EvidenceAssemblyService service;

    @BeforeEach
    void setUp() {
        service = new EvidenceAssemblyService(contextQueryService, vectorRetrievalService);
    }

    @Test
    void rejectsBlankOrNullQuestion() {
        assertThrows(IllegalArgumentException.class, () -> service.assembleEvidence(null));
        assertThrows(IllegalArgumentException.class, () -> service.assembleEvidence(""));
        assertThrows(IllegalArgumentException.class, () -> service.assembleEvidence("   \t\n "));
    }

    @Test
    void rejectsInvalidTopK() {
        assertThrows(IllegalArgumentException.class, () -> service.assembleEvidence("Why PostgreSQL?", 0));
        assertThrows(IllegalArgumentException.class, () -> service.assembleEvidence("Why PostgreSQL?", -1));
    }

    @Test
    void combinesGraphFactsAndRetrievedPassagesSuccessfully() {
        String question = "Why did we choose PostgreSQL for the Payment Service?";

        DecisionContext ctx = new DecisionContext(
                "Use PostgreSQL for payments",
                "Payment Service",
                "PostgreSQL",
                "Payments",
                "Payment Architecture Design",
                "DOC-001",
                List.of("(Decision: Use PostgreSQL for payments)-[AFFECTS]->(Project: Payment Service)",
                        "(Project: Payment Service)-[USES]->(System: PostgreSQL)")
        );
        when(contextQueryService.findForQuestion(question)).thenReturn(List.of(ctx));

        RetrievedChunk chunk = new RetrievedChunk(
                "DOC-001#chunk-0",
                0,
                "PostgreSQL was chosen due to strict ACID requirements for payments.",
                "DOC-001",
                "Payment Architecture Design",
                0.12,
                0.88,
                Map.of("distance", 0.12)
        );
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(List.of(chunk));

        EvidencePackage pkg = service.assembleEvidence(question, 5);

        assertEquals(question, pkg.question());
        assertTrue(pkg.hasGraphContext());
        assertTrue(pkg.hasDocumentEvidence());
        assertTrue(pkg.hasAnyEvidence());

        assertEquals(2, pkg.graphFacts().size());
        assertEquals(1, pkg.decisionContexts().size());
        assertEquals(1, pkg.retrievedPassages().size());

        RetrievedPassage p = pkg.retrievedPassages().get(0);
        assertEquals("DOC-001#chunk-0", p.chunkId());
        assertEquals("DOC-001", p.sourceId());
        assertEquals("Payment Architecture Design", p.documentTitle());
        assertEquals("PostgreSQL was chosen due to strict ACID requirements for payments.", p.text());
        assertEquals(0.12, p.distanceScore());
        assertEquals(0.88, p.similarityScore());

        List<Evidence> sources = pkg.distinctSources();
        assertEquals(1, sources.size());
        assertEquals("Payment Architecture Design", sources.get(0).documentTitle());
        assertEquals("DOC-001", sources.get(0).sourceId());
    }

    @Test
    void handlesGraphOnlyEvidenceWhenNoDocumentPassagesRetrieved() {
        String question = "What system does Payment Service use?";

        DecisionContext ctx = new DecisionContext(
                "Use PostgreSQL for payments",
                "Payment Service",
                "PostgreSQL",
                "Payments",
                "Payment Architecture Design",
                "DOC-001",
                List.of("(Project: Payment Service)-[USES]->(System: PostgreSQL)")
        );
        when(contextQueryService.findForQuestion(question)).thenReturn(List.of(ctx));
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(Collections.emptyList());

        EvidencePackage pkg = service.assembleEvidence(question);

        assertTrue(pkg.hasGraphContext());
        assertFalse(pkg.hasDocumentEvidence());
        assertTrue(pkg.hasAnyEvidence());
        assertEquals(1, pkg.graphFacts().size());
        assertTrue(pkg.retrievedPassages().isEmpty());

        // Limitations must explicitly state document text is absent
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("no written rationale or documentation text was retrieved")));
    }

    @Test
    void handlesDocumentOnlyEvidenceWhenNoGraphContextMatches() {
        String question = "What are the rules for database connection pooling?";

        when(contextQueryService.findForQuestion(question)).thenReturn(Collections.emptyList());

        RetrievedChunk chunk = new RetrievedChunk(
                "DOC-001#chunk-1",
                1,
                "Connection pool size is capped at 50 connections with 5s timeout.",
                "DOC-001",
                "Payment Architecture Design",
                0.25,
                0.75,
                Map.of()
        );
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(List.of(chunk));

        EvidencePackage pkg = service.assembleEvidence(question);

        assertFalse(pkg.hasGraphContext());
        assertTrue(pkg.hasDocumentEvidence());
        assertTrue(pkg.hasAnyEvidence());
        assertTrue(pkg.graphFacts().isEmpty());
        assertEquals(1, pkg.retrievedPassages().size());

        // Limitations must explicitly state graph relationships are absent
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("No structural relationships")));
    }

    @Test
    void handlesNoEvidenceFoundSafely() {
        String question = "What is our company holiday policy?";

        when(contextQueryService.findForQuestion(question)).thenReturn(Collections.emptyList());
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(Collections.emptyList());

        EvidencePackage pkg = service.assembleEvidence(question);

        assertFalse(pkg.hasGraphContext());
        assertFalse(pkg.hasDocumentEvidence());
        assertFalse(pkg.hasAnyEvidence());
        assertTrue(pkg.graphFacts().isEmpty());
        assertTrue(pkg.retrievedPassages().isEmpty());

        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("No graph facts or document evidence were found")));
    }

    @Test
    void preservesStrictProvenanceWithoutFabricatingRationale() {
        String question = "Tell me about payments";
        when(contextQueryService.findForQuestion(question)).thenReturn(Collections.emptyList());

        RetrievedChunk chunk = new RetrievedChunk(
                "DOC-001#chunk-0",
                0,
                "Real text from document DOC-001.",
                "DOC-001",
                "Payment Architecture Design",
                0.10,
                0.90,
                Map.of()
        );
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(List.of(chunk));

        EvidencePackage pkg = service.assembleEvidence(question);

        RetrievedPassage p = pkg.retrievedPassages().get(0);
        assertEquals("DOC-001", p.sourceId());
        assertEquals("Payment Architecture Design", p.documentTitle());
        assertEquals("DOC-001#chunk-0", p.chunkId());
        // Factual limitation guarantee
        assertTrue(pkg.limitations().stream().anyMatch(l -> l.contains("no unconfirmed rationale or external metadata has been fabricated")));
    }

    @Test
    void handlesMissingSourceMetadataSafelyWithoutCrashing() {
        String question = "Generic query";
        DecisionContext incompleteCtx = new DecisionContext(
                "Decision", "Project", "System", "Team", null, null, List.of("fact-1")
        );
        when(contextQueryService.findForQuestion(question)).thenReturn(List.of(incompleteCtx));

        RetrievedChunk chunkWithMissingMetadata = new RetrievedChunk(
                "CHUNK-X", 0, "Some text", null, null, 0.4, 0.6, null
        );
        when(vectorRetrievalService.retrieve(question, 5)).thenReturn(List.of(chunkWithMissingMetadata));

        EvidencePackage pkg = service.assembleEvidence(question);

        assertTrue(pkg.hasAnyEvidence());
        assertEquals(1, pkg.graphFacts().size());
        assertEquals(1, pkg.retrievedPassages().size());
        // distinctSources safely ignores entries missing title or sourceId
        assertTrue(pkg.distinctSources().isEmpty());
    }

    @Test
    void propagatesGraphUnavailableExceptionWhenGraphClientFails() {
        String question = "Why PostgreSQL?";
        when(contextQueryService.findForQuestion(question))
                .thenThrow(new GraphUnavailableException("FalkorDB connection dropped"));

        assertThrows(GraphUnavailableException.class, () -> service.assembleEvidence(question));
    }

    @Test
    void propagatesGraphUnavailableExceptionWhenRetrievalServiceFails() {
        String question = "Why PostgreSQL?";
        when(contextQueryService.findForQuestion(question)).thenReturn(Collections.emptyList());
        when(vectorRetrievalService.retrieve(question, 5))
                .thenThrow(new GraphUnavailableException("Vector index query timeout"));

        assertThrows(GraphUnavailableException.class, () -> service.assembleEvidence(question));
    }
}
