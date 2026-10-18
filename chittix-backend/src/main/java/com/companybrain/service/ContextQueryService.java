package com.companybrain.service;

import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.GraphDataResponse;
import com.companybrain.dto.GraphNodeDto;
import com.companybrain.dto.GraphRelationshipDto;
import com.companybrain.dto.RetrievalScope;
import com.companybrain.exception.GraphUnavailableException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Cypher queries for Decision -> Project -> System chains with supporting documents and repository resolution. */
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
               OR toLower($question) CONTAINS toLower(doc.title)
               OR toLower($question) CONTAINS toLower(doc.source_id)
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

    /**
     * Finds repositories in FalkorDB whose name or full_name is explicitly mentioned in the question.
     */
    public List<String> findRepositoriesForQuestion(String question) {
        if (question == null || question.isBlank()) {
            return Collections.emptyList();
        }
        String cypher = "MATCH (r:Repository) RETURN r.name AS name, r.full_name AS fullName";
        List<Map<String, Object>> rows;
        try {
            rows = graphClient.query(cypher, Collections.emptyMap());
        } catch (Exception e) {
            return Collections.emptyList();
        }
        if (rows == null || rows.isEmpty()) {
            return Collections.emptyList();
        }

        List<String> matched = new ArrayList<>();
        String normalizedQuestion = question.toLowerCase(java.util.Locale.ROOT);

        for (Map<String, Object> row : rows) {
            String name = str(row, "name");
            String fullName = str(row, "fullName");
            String canonical = (fullName != null && !fullName.isBlank()) ? fullName : name;
            if (canonical == null || canonical.isBlank()) {
                continue;
            }

            boolean matches = false;
            if (fullName != null && normalizedQuestion.contains(fullName.toLowerCase(java.util.Locale.ROOT))) {
                matches = true;
            } else if (name != null && normalizedQuestion.contains(name.toLowerCase(java.util.Locale.ROOT))) {
                matches = true;
            } else {
                // Also check repository base name (e.g. "AI-Insurance-Review" from "Rahulram240/AI-Insurance-Review")
                String baseName = canonical;
                if (baseName.contains("/")) {
                    baseName = baseName.substring(baseName.lastIndexOf('/') + 1);
                }
                String normBase = baseName.toLowerCase(java.util.Locale.ROOT);
                if (normalizedQuestion.contains(normBase)) {
                    matches = true;
                } else if (normBase.contains("-") && normalizedQuestion.contains(normBase.replace('-', ' '))) {
                    matches = true;
                } else if (normBase.contains("_") && normalizedQuestion.contains(normBase.replace('_', ' '))) {
                    matches = true;
                }
            }

            if (matches && !matched.contains(canonical)) {
                matched.add(canonical);
            }
        }
        return matched;
    }

    /**
     * Determines the explicit, verified retrieval scope for a question based on
     * graph entities and explicitly referenced repositories.
     */
    public RetrievalScope determineScope(String question) {
        if (question == null || question.isBlank()) {
            return RetrievalScope.unscoped();
        }

        List<DecisionContext> contexts = findForQuestion(question);
        Set<String> authorizedDocIds = new HashSet<>();
        String contextName = null;
        if (contexts != null && !contexts.isEmpty()) {
            for (DecisionContext c : contexts) {
                if (c.sourceId() != null && !c.sourceId().isBlank()) {
                    authorizedDocIds.add(c.sourceId());
                }
                if (contextName == null && c.project() != null) {
                    contextName = c.project();
                }
            }
        }

        List<String> matchedRepos = new ArrayList<>(findRepositoriesForQuestion(question));

        // Check Confluence spaces and pages
        Set<String> confluenceDocIds = findConfluenceEntitiesForQuestion(question);
        authorizedDocIds.addAll(confluenceDocIds);

        // Check provider-level mentions
        String normQ = question.toLowerCase(java.util.Locale.ROOT);
        if (normQ.contains("gitlab")) {
            matchedRepos.addAll(findReposBySource("gitlab"));
        }
        if (normQ.contains("bitbucket")) {
            matchedRepos.addAll(findReposBySource("bitbucket"));
        }
        if (normQ.contains("confluence") || normQ.contains("wiki")) {
            authorizedDocIds.addAll(findAllConfluenceDocIds());
        }

        if (!authorizedDocIds.isEmpty() && !matchedRepos.isEmpty()) {
            return RetrievalScope.hybrid(authorizedDocIds, Set.copyOf(matchedRepos),
                    "Hybrid scope: " + (contextName != null ? contextName : "Architecture/Docs") + " and " + String.join(", ", matchedRepos));
        } else if (!authorizedDocIds.isEmpty()) {
            return RetrievalScope.architecture(authorizedDocIds, contextName != null ? contextName : "Knowledge Base");
        } else if (!matchedRepos.isEmpty()) {
            return RetrievalScope.repository(Set.copyOf(matchedRepos));
        } else {
            return RetrievalScope.unscoped();
        }
    }

    public Set<String> findConfluenceEntitiesForQuestion(String question) {
        if (question == null || question.isBlank()) return Collections.emptySet();
        Set<String> docIds = new HashSet<>();
        String normQ = question.toLowerCase(java.util.Locale.ROOT);

        try {
            List<Map<String, Object>> spaceRows = graphClient.query(
                    "MATCH (s:Space) RETURN s.key AS key, s.name AS name, s.source_id AS sourceId",
                    Collections.emptyMap()
            );
            for (Map<String, Object> r : spaceRows) {
                String key = str(r, "key");
                String name = str(r, "name");
                String sid = str(r, "sourceId");
                if ((key != null && normQ.contains(key.toLowerCase(java.util.Locale.ROOT)))
                        || (name != null && normQ.contains(name.toLowerCase(java.util.Locale.ROOT)))) {
                    if (sid != null) docIds.add(sid);
                }
            }
        } catch (Exception ignored) {}

        try {
            List<Map<String, Object>> docRows = graphClient.query(
                    "MATCH (d:Document) WHERE d.source_id STARTS WITH 'confluence:' RETURN d.title AS title, d.source_id AS sourceId",
                    Collections.emptyMap()
            );
            for (Map<String, Object> r : docRows) {
                String title = str(r, "title");
                String sid = str(r, "sourceId");
                if (title != null && normQ.contains(title.toLowerCase(java.util.Locale.ROOT))) {
                    if (sid != null) docIds.add(sid);
                }
            }
        } catch (Exception ignored) {}

        return docIds;
    }

    public List<String> findReposBySource(String sourceName) {
        try {
            List<Map<String, Object>> rows = graphClient.query(
                    "MATCH (r:Repository) WHERE r.source = $src OR r.source_id STARTS WITH $prefix RETURN coalesce(r.full_name, r.name) AS fullName",
                    Map.of("src", sourceName, "prefix", sourceName + ":")
            );
            List<String> list = new ArrayList<>();
            for (Map<String, Object> r : rows) {
                String fn = str(r, "fullName");
                if (fn != null && !fn.isBlank()) list.add(fn);
            }
            return list;
        } catch (Exception e) {
            return Collections.emptyList();
        }
    }

    public Set<String> findAllConfluenceDocIds() {
        try {
            List<Map<String, Object>> rows = graphClient.query(
                    "MATCH (d:Document) WHERE d.source_id STARTS WITH 'confluence:' RETURN d.source_id AS sourceId",
                    Collections.emptyMap()
            );
            Set<String> set = new HashSet<>();
            for (Map<String, Object> r : rows) {
                String sid = str(r, "sourceId");
                if (sid != null) set.add(sid);
            }
            return set;
        } catch (Exception e) {
            return Collections.emptySet();
        }
    }

    /**
     * Retrieves the real graph topology (nodes and relationships) from FalkorDB,
     * excluding internal DocumentChunk vector index nodes.
     */
    public GraphDataResponse getKnowledgeGraph() {
        String nodeCypher = """
                MATCH (n)
                WHERE NOT n:DocumentChunk
                RETURN id(n) AS id, labels(n) AS labels,
                       coalesce(n.name, n.title, n.full_name, 'Node') AS name,
                       n.source_id AS sourceId,
                       properties(n) AS properties
                ORDER BY id
                """;

        String relCypher = """
                MATCH (s)-[r]->(t)
                WHERE NOT s:DocumentChunk AND NOT t:DocumentChunk
                RETURN id(r) AS id, id(s) AS source, id(t) AS target, type(r) AS type
                ORDER BY id
                """;

        List<GraphNodeDto> nodes = new ArrayList<>();
        List<GraphRelationshipDto> relationships = new ArrayList<>();

        try {
            List<Map<String, Object>> nodeRows = graphClient.query(nodeCypher, Collections.emptyMap());
            for (Map<String, Object> row : nodeRows) {
                String id = String.valueOf(row.get("id"));
                String name = str(row, "name");
                String sourceId = str(row, "sourceId");

                String type = firstLabel(row.get("labels"));

                Map<String, String> propMap = new LinkedHashMap<>();
                Object propsObj = row.get("properties");
                if (propsObj instanceof Map<?, ?> map) {
                    for (Map.Entry<?, ?> entry : map.entrySet()) {
                        if (entry.getKey() != null && entry.getValue() != null) {
                            String key = entry.getKey().toString();
                            if (!"embedding".equalsIgnoreCase(key)) {
                                propMap.put(key, entry.getValue().toString());
                            }
                        }
                    }
                }

                nodes.add(new GraphNodeDto(id, name, type, sourceId, propMap));
            }

            List<Map<String, Object>> relRows = graphClient.query(relCypher, Collections.emptyMap());
            for (Map<String, Object> row : relRows) {
                String id = String.valueOf(row.get("id"));
                String source = String.valueOf(row.get("source"));
                String target = String.valueOf(row.get("target"));
                String type = str(row, "type");
                relationships.add(new GraphRelationshipDto(id, source, target, type));
            }
        } catch (GraphUnavailableException e) {
            throw e;
        } catch (RuntimeException e) {
            throw new GraphUnavailableException(
                    "Failed to read topology from graph '" + graphClient.graphName() + "': " + e.getMessage(), e);
        }

        return new GraphDataResponse(graphClient.graphName(), nodes.size(), relationships.size(), nodes, relationships);
    }

    private static String firstLabel(Object labelsObj) {
        if (labelsObj instanceof List<?> list && !list.isEmpty() && list.get(0) != null) {
            return String.valueOf(list.get(0));
        }
        if (labelsObj instanceof Object[] arr && arr.length > 0 && arr[0] != null) {
            return String.valueOf(arr[0]);
        }
        if (labelsObj instanceof Iterable<?> iterable) {
            for (Object item : iterable) {
                if (item != null) {
                    return String.valueOf(item);
                }
            }
        }
        return "Node";
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
