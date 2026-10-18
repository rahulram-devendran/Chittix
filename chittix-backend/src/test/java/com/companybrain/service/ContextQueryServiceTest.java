package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.GraphDataResponse;
import com.companybrain.dto.RetrievalScope;
import com.companybrain.exception.GraphUnavailableException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ContextQueryServiceTest {

    @Mock
    GraphClient graphClient;

    private static Map<String, Object> row(String team) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("decision", "D-title");
        row.put("project", "P-name");
        row.put("system", "S-name");
        row.put("documentTitle", "Doc-title");
        row.put("sourceId", "SRC-1");
        row.put("affectsRel", "AFFECTS");
        row.put("usesRel", "USES");
        row.put("supportRel", "SUPPORTED_BY");
        row.put("ownerTeam", team);
        row.put("ownsRel", team == null ? null : "OWNS");
        return row;
    }

    @Test
    void mapsGraphRowToContextWithEvidencePath() {
        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(row("T-name")));
        ContextQueryService service = new ContextQueryService(graphClient, "payment");

        List<DecisionContext> result = service.findPaymentsContext();

        assertEquals(1, result.size());
        DecisionContext c = result.get(0);
        assertEquals("D-title", c.decision());
        assertEquals("P-name", c.project());
        assertEquals("S-name", c.system());
        assertEquals("Doc-title", c.documentTitle());
        assertEquals("SRC-1", c.sourceId());
        assertEquals("T-name", c.ownerTeam());
        assertEquals(4, c.evidencePath().size());
        assertTrue(c.evidencePath().get(0).contains("-[AFFECTS]->"));
    }

    @Test
    void omitsOwnershipStepWhenNoTeam() {
        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(row(null)));
        ContextQueryService service = new ContextQueryService(graphClient, "payment");

        DecisionContext c = service.findPaymentsContext().get(0);

        assertNull(c.ownerTeam());
        assertEquals(3, c.evidencePath().size());
    }

    @Test
    void passesQuestionAsParameterNotInQueryText() {
        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of());
        ContextQueryService service = new ContextQueryService(graphClient, "payment");

        service.findForQuestion("Why PostgreSQL?");

        ArgumentCaptor<String> cypher = ArgumentCaptor.forClass(String.class);
        verify(graphClient).query(cypher.capture(), eq(Map.of("question", "Why PostgreSQL?")));
        assertTrue(cypher.getValue().contains("$question"));
        assertTrue(!cypher.getValue().contains("Why PostgreSQL?"));
    }

    @Test
    void findRepositoriesForQuestionMatchesRepositoryByNameAndNormalizedName() {
        when(graphClient.query(anyString(), anyMap())).thenReturn(List.of(
                Map.of("url", "https://github.com/Rahulram240/AI-Insurance-Review", "name", "Rahulram240/AI-Insurance-Review")
        ));
        ContextQueryService service = new ContextQueryService(graphClient, "payment");

        // Direct hyphenated mention
        List<String> reposHyphen = service.findRepositoriesForQuestion("Tell me about AI-Insurance-Review");
        assertEquals(1, reposHyphen.size());
        assertEquals("Rahulram240/AI-Insurance-Review", reposHyphen.get(0));

        // Space-separated mention
        List<String> reposSpace = service.findRepositoriesForQuestion("What is in AI Insurance Review repository?");
        assertEquals(1, reposSpace.size());
        assertEquals("Rahulram240/AI-Insurance-Review", reposSpace.get(0));

        // Unrelated query
        List<String> reposNone = service.findRepositoriesForQuestion("Why did we choose PostgreSQL for Payment Service?");
        assertTrue(reposNone.isEmpty());
    }

    @Test
    void determineScopeResolvesArchitectureScopeWhenDecisionContextMatches() {
        // First query in determineScope: findForQuestion
        // Second query in determineScope: findRepositoriesForQuestion
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(row("Payments"))) // findForQuestion
                .thenReturn(List.of());              // findRepositoriesForQuestion

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        RetrievalScope scope = service.determineScope("Why did we choose PostgreSQL for the Payment Service?");

        assertEquals(RetrievalScope.ScopeType.ARCHITECTURE, scope.type());
        assertTrue(scope.authorizedSourceIds().contains("SRC-1"));
        assertTrue(scope.isAllowed("SRC-1"));
        assertFalse(scope.isAllowed("https://github.com/Rahulram240/AI-Insurance-Review/commit/123"));
    }

    @Test
    void determineScopeResolvesRepositoryScopeWhenRepositoryMentioned() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of()) // findForQuestion
                .thenReturn(List.of(Map.of("url", "https://github.com/Rahulram240/AI-Insurance-Review", "name", "Rahulram240/AI-Insurance-Review"))); // findRepositoriesForQuestion

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        RetrievalScope scope = service.determineScope("What commits exist in AI Insurance Review?");

        assertEquals(RetrievalScope.ScopeType.REPOSITORY, scope.type());
        assertTrue(scope.authorizedRepositories().contains("Rahulram240/AI-Insurance-Review"));
        assertTrue(scope.isAllowed("https://github.com/Rahulram240/AI-Insurance-Review/commit/123"));
        assertFalse(scope.isAllowed("SRC-1"));
    }

    @Test
    void determineScopeReturnsUnscopedWhenNeitherMatches() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of()) // findForQuestion
                .thenReturn(List.of()); // findRepositoriesForQuestion

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        RetrievalScope scope = service.determineScope("What is the weather today?");

        assertEquals(RetrievalScope.ScopeType.UNSCOPED, scope.type());
        assertFalse(scope.isAllowed("SRC-1"));
        assertFalse(scope.isAllowed("https://github.com/Rahulram240/AI-Insurance-Review/commit/123"));
    }

    @Test
    void getKnowledgeGraphMapsNodesAndRelationships() {
        when(graphClient.graphName()).thenReturn("company_brain");
        Map<String, Object> nodeRow = new LinkedHashMap<>();
        nodeRow.put("id", 10L);
        nodeRow.put("labels", List.of("Project"));
        nodeRow.put("name", "Payment Service");
        nodeRow.put("sourceId", null);
        nodeRow.put("properties", Map.of("name", "Payment Service", "embedding", "skip-me"));

        Map<String, Object> relRow = new LinkedHashMap<>();
        relRow.put("id", 99L);
        relRow.put("source", 10L);
        relRow.put("target", 11L);
        relRow.put("type", "USES");

        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of(nodeRow))
                .thenReturn(List.of(relRow));

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        GraphDataResponse graph = service.getKnowledgeGraph();

        assertEquals("company_brain", graph.graph());
        assertEquals(1, graph.nodeCount());
        assertEquals(1, graph.relationshipCount());
        assertEquals("Project", graph.nodes().get(0).type());
        assertEquals("Payment Service", graph.nodes().get(0).name());
        assertFalse(graph.nodes().get(0).properties().containsKey("embedding"));
        assertEquals("USES", graph.relationships().get(0).type());
        assertEquals("10", graph.relationships().get(0).source());
    }

    @Test
    void getKnowledgeGraphPropagatesGraphUnavailable() {
        when(graphClient.query(anyString(), anyMap()))
                .thenThrow(new GraphUnavailableException("db down", null));
        ContextQueryService service = new ContextQueryService(graphClient, "payment");

        assertThrows(GraphUnavailableException.class, service::getKnowledgeGraph);
    }

    @Test
    void determineScopeResolvesConfluenceDocWhenPageTitleMatches() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of()) // findForQuestion
                .thenReturn(List.of()) // findRepositoriesForQuestion
                .thenReturn(List.of(Map.of("key", "ENG", "name", "Engineering", "sourceId", "confluence:space:ENG"))) // findConfluenceEntities: space
                .thenReturn(List.of(Map.of("title", "Architecture Decisions", "sourceId", "confluence:page:999"))); // findConfluenceEntities: docs

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        RetrievalScope scope = service.determineScope("What is documented in Architecture Decisions page?");

        assertEquals(RetrievalScope.ScopeType.ARCHITECTURE, scope.type());
        assertTrue(scope.authorizedSourceIds().contains("confluence:page:999"));
        assertTrue(scope.isAllowed("confluence:page:999"));
    }

    @Test
    void determineScopeResolvesGitLabReposWhenGitLabKeywordUsed() {
        when(graphClient.query(anyString(), anyMap()))
                .thenReturn(List.of()) // findForQuestion
                .thenReturn(List.of()) // findRepositoriesForQuestion
                .thenReturn(List.of()) // findConfluenceEntities: space
                .thenReturn(List.of()) // findConfluenceEntities: docs
                .thenReturn(List.of(Map.of("fullName", "org/gitlab-service"))); // findReposBySource: gitlab

        ContextQueryService service = new ContextQueryService(graphClient, "payment");
        RetrievalScope scope = service.determineScope("What gitlab projects are imported?");

        assertEquals(RetrievalScope.ScopeType.REPOSITORY, scope.type());
        assertTrue(scope.authorizedRepositories().contains("org/gitlab-service"));
        assertTrue(scope.isAllowed("gitlab:repo:org/gitlab-service"));
    }

    @Test
    void retrievalScopeMatchesSourceSupportsAllProviders() {
        RetrievalScope scope = RetrievalScope.repository(java.util.Set.of("acme/payment-service"));

        assertTrue(scope.isAllowed("https://github.com/acme/payment-service/commit/123"));
        assertTrue(scope.isAllowed("gitlab:repo:acme/payment-service"));
        assertTrue(scope.isAllowed("gitlab:commit:acme/payment-service:abc"));
        assertTrue(scope.isAllowed("bitbucket:repo:acme/payment-service"));
        assertTrue(scope.isAllowed("bitbucket:commit:acme/payment-service:xyz"));
        assertFalse(scope.isAllowed("gitlab:repo:other/service"));
    }
}

