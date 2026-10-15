package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Cypher queries for Decision -> Project -> System chains with supporting documents. */
@Service
public class ContextQueryService {

    private static final String MATCH = """
            MATCH (d:Decision)-[r1:AFFECTS]->(p:Project)-[r2:USES]->(s:System),
                  (d)-[r3:SUPPORTED_BY]->(doc:Document)
            """;

    private static final String RETURN_CLAUSE = """
            OPTIONAL MATCH (t:Team)-[r4:OWNS]->(p)
            RETURN d.title AS decision, p.name AS project, s.name AS system,
                   doc.title AS documentTitle, doc.source_id AS sourceId,
                   type(r1) AS affectsRel, type(r2) AS usesRel, type(r3) AS supportRel,
                   t.name AS ownerTeam, type(r4) AS ownsRel
            ORDER BY decision, project
            """;

    private static final String PROJECT_FILTER =
            "WHERE toLower(p.name) CONTAINS toLower($keyword)\n";

    private static final String QUESTION_FILTER = """
            WHERE toLower($question) CONTAINS toLower(p.name)
               OR toLower($question) CONTAINS toLower(s.name)
               OR toLower($question) CONTAINS toLower(d.title)
            """;

    private final GraphClient graphClient;
    private final String demoProjectKeyword;

    public ContextQueryService(GraphClient graphClient,
                               @Value("${brain.demo.project-keyword:payment}") String demoProjectKeyword) {
        this.graphClient = graphClient;
        this.demoProjectKeyword = demoProjectKeyword;
    }

    /** Contexts for projects whose name contains the configured demo keyword (default "payment"). */
    public List<DecisionContext> findPaymentsContext() {
        return map(graphClient.query(MATCH + PROJECT_FILTER + RETURN_CLAUSE,
                Map.of("keyword", demoProjectKeyword)));
    }

    /** Contexts whose project, system or decision title is mentioned in the question text. */
    public List<DecisionContext> findForQuestion(String question) {
        return map(graphClient.query(MATCH + QUESTION_FILTER + RETURN_CLAUSE,
                Map.of("question", question)));
    }

    private List<DecisionContext> map(List<Map<String, Object>> rows) {
        List<DecisionContext> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            String decision = str(row, "decision");
            String project = str(row, "project");
            String system = str(row, "system");
            String docTitle = str(row, "documentTitle");
            String sourceId = str(row, "sourceId");
            String team = str(row, "ownerTeam");

            List<String> path = new ArrayList<>();
            path.add("(Decision: " + decision + ")-[" + str(row, "affectsRel") + "]->(Project: " + project + ")");
            path.add("(Project: " + project + ")-[" + str(row, "usesRel") + "]->(System: " + system + ")");
            path.add("(Decision: " + decision + ")-[" + str(row, "supportRel") + "]->(Document: "
                    + docTitle + ", " + sourceId + ")");
            if (team != null) {
                path.add("(Team: " + team + ")-[" + str(row, "ownsRel") + "]->(Project: " + project + ")");
            }
            result.add(new DecisionContext(decision, project, system, team, docTitle, sourceId, path));
        }
        return result;
    }

    private static String str(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : String.valueOf(value);
    }
}
