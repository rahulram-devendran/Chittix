package com.companybrain.provider.adapter;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.RetrievedPassage;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class OllamaProviderAdapterTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    private LlmProperties properties;
    private MockRestServiceServer mockServer;
    private OllamaProviderAdapter adapter;

    private final List<RetrievedPassage> fiveCommitPassages = List.of(
            new RetrievedPassage(
                    "github:commit:Rahulram240/AI-Insurance-Review@6039173#chunk-0",
                    0,
                    """
                    Commit in Rahulram240/AI-Insurance-Review (603917311...)
                    Author: Rahulram <rahul@example.com>
                    Date: 2026-10-15T10:00:00Z
                    URL: https://github.com/Rahulram240/AI-Insurance-Review/commit/6039173
                    Message:
                    feat: initial policy review parser
                    """,
                    "github:commit:Rahulram240/AI-Insurance-Review@6039173",
                    "Commit 6039173: feat: initial policy review parser",
                    0.10,
                    0.90
            ),
            new RetrievedPassage(
                    "github:commit:Rahulram240/AI-Insurance-Review@e7e91cc#chunk-0",
                    0,
                    """
                    Commit in Rahulram240/AI-Insurance-Review (e7e91cc22...)
                    Author: Rahulram <rahul@example.com>
                    Date: 2026-10-15T14:00:00Z
                    URL: https://github.com/Rahulram240/AI-Insurance-Review/commit/e7e91cc
                    Message:
                    fix: null handling in claim assessment
                    """,
                    "github:commit:Rahulram240/AI-Insurance-Review@e7e91cc",
                    "Commit e7e91cc: fix: null handling in claim assessment",
                    0.12,
                    0.88
            ),
            new RetrievedPassage(
                    "github:commit:Rahulram240/AI-Insurance-Review@80b0cdc#chunk-0",
                    0,
                    """
                    Commit in Rahulram240/AI-Insurance-Review (80b0cdc33...)
                    Author: Rahulram <rahul@example.com>
                    Date: 2026-10-16T09:30:00Z
                    URL: https://github.com/Rahulram240/AI-Insurance-Review/commit/80b0cdc
                    Message:
                    test: add test suite for risk validation
                    """,
                    "github:commit:Rahulram240/AI-Insurance-Review@80b0cdc",
                    "Commit 80b0cdc: test: add test suite for risk validation",
                    0.15,
                    0.85
            ),
            new RetrievedPassage(
                    "github:commit:Rahulram240/AI-Insurance-Review@e780ab7#chunk-0",
                    0,
                    """
                    Commit in Rahulram240/AI-Insurance-Review (e780ab744...)
                    Author: Rahulram <rahul@example.com>
                    Date: 2026-10-16T15:00:00Z
                    URL: https://github.com/Rahulram240/AI-Insurance-Review/commit/e780ab7
                    Message:
                    docs: update insurance underwriting guide
                    """,
                    "github:commit:Rahulram240/AI-Insurance-Review@e780ab7",
                    "Commit e780ab7: docs: update insurance underwriting guide",
                    0.18,
                    0.82
            ),
            new RetrievedPassage(
                    "github:commit:Rahulram240/AI-Insurance-Review@9df2845#chunk-0",
                    0,
                    """
                    Commit in Rahulram240/AI-Insurance-Review (9df284555...)
                    Author: Rahulram <rahul@example.com>
                    Date: 2026-10-17T11:00:00Z
                    URL: https://github.com/Rahulram240/AI-Insurance-Review/commit/9df2845
                    Message:
                    chore: release v1.0.0
                    """,
                    "github:commit:Rahulram240/AI-Insurance-Review@9df2845",
                    "Commit 9df2845: chore: release v1.0.0",
                    0.20,
                    0.80
            )
    );

    @BeforeEach
    void setUp() {
        properties = new LlmProperties();
        properties.setBaseUrl("http://localhost:11434");
        properties.setModel("qwen2.5:1.5b");
        properties.setTemperature(0.1);
        properties.setMaxTokens(500);

        RestClient.Builder builder = RestClient.builder();
        mockServer = MockRestServiceServer.bindTo(builder).build();
        adapter = new OllamaProviderAdapter(builder.build());
    }

    @Test
    void generatesGroundedAnswerForRepositoryCommitInventoryWithoutContradiction() throws Exception {
        EvidencePackage evidence = new EvidencePackage(
                "What commits and pull requests were imported for Rahulram240/AI-Insurance-Review?",
                List.of(),
                List.of(),
                fiveCommitPassages,
                false,
                true,
                true,
                List.of("No structural relationships were identified in the company graph.")
        );

        String mockOllamaResponse = objectMapper.writeValueAsString(Map.of(
                "model", "qwen2.5:1.5b",
                "message", Map.of(
                        "role", "assistant",
                        "content", """
                        Based on the verified records, five commits were imported for Rahulram240/AI-Insurance-Review:
                        - Commit 6039173: feat: initial policy review parser [github:commit:Rahulram240/AI-Insurance-Review@6039173]
                        - Commit e7e91cc: fix: null handling in claim assessment [github:commit:Rahulram240/AI-Insurance-Review@e7e91cc]
                        - Commit 80b0cdc: test: add test suite for risk validation [github:commit:Rahulram240/AI-Insurance-Review@80b0cdc]
                        - Commit e780ab7: docs: update insurance underwriting guide [github:commit:Rahulram240/AI-Insurance-Review@e780ab7]
                        - Commit 9df2845: chore: release v1.0.0 [github:commit:Rahulram240/AI-Insurance-Review@9df2845]

                        No pull requests were found in the retrieved evidence.
                        """
                ),
                "done", true
        ));

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Rahulram240/AI-Insurance-Review")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("6039173")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("e7e91cc")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("REPOSITORY INVENTORY & LISTING QUESTIONS")))
                .andRespond(withSuccess(mockOllamaResponse, MediaType.APPLICATION_JSON));

        GeneratedAnswer answer = adapter.generateAnswer(evidence, properties);

        assertNotNull(answer);
        assertEquals("LLM_GROUNDED", answer.answerType());
        assertFalse(answer.fallbackUsed());
        assertFalse(answer.answerText().contains("there are no specific commits or pull requests mentioned"));
        assertTrue(answer.answerText().contains("five commits were imported for Rahulram240/AI-Insurance-Review"));
        assertTrue(answer.answerText().contains("No pull requests were found in the retrieved evidence"));

        // Verify citations were properly extracted and validated against commit source IDs
        assertEquals(5, answer.citedPassages().size());
        assertTrue(answer.citedPassages().stream().anyMatch(p -> p.sourceId().contains("6039173")));
        assertTrue(answer.citedPassages().stream().anyMatch(p -> p.sourceId().contains("9df2845")));

        mockServer.verify();
    }

    @Test
    void fallsBackDeterministicallyWhenOllamaReturnsServerError() {
        EvidencePackage evidence = new EvidencePackage(
                "What commits and pull requests were imported for Rahulram240/AI-Insurance-Review?",
                List.of(),
                List.of(),
                fiveCommitPassages,
                false,
                true,
                true,
                List.of()
        );

        mockServer.expect(requestTo("http://localhost:11434/api/chat"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withServerError());

        GeneratedAnswer answer = adapter.generateAnswer(evidence, properties);

        assertNotNull(answer);
        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertTrue(answer.answerText().contains("Deterministic fallback"));
        assertEquals(5, answer.citedPassages().size());

        mockServer.verify();
    }
}
