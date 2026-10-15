package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
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
}
