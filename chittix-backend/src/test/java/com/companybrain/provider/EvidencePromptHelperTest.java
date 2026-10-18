package com.companybrain.provider;

import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.RetrievedPassage;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EvidencePromptHelperTest {

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

    @Test
    void buildSystemPromptContainsExplicitRepositoryInventoryAndGroundingInstructions() {
        String systemPrompt = EvidencePromptHelper.buildSystemPrompt();
        assertNotNull(systemPrompt);

        // Requirement 3: Explicit instructions to answer repository inventory questions directly from supplied evidence
        assertTrue(systemPrompt.contains("REPOSITORY INVENTORY & LISTING QUESTIONS"),
                "System prompt must have explicit section for repository inventory questions");
        assertTrue(systemPrompt.contains("NEVER claim that records, commits, pull requests, or documents are absent, missing, or unmentioned when matching records appear in the retrieved evidence"),
                "System prompt must explicitly forbid claiming records are absent when present");

        // Requirement 4: Distinguish no PRs found in retrieved evidence from repository having no PRs
        assertTrue(systemPrompt.contains("DISTINGUISH MISSING ENTITIES FROM ABSENT EVIDENCE"),
                "System prompt must guide distinguishing missing evidence from absent repo data");
        assertTrue(systemPrompt.contains("no pull requests were found in the retrieved evidence"),
                "System prompt must require explicit 'no pull requests were found in the retrieved evidence' wording");
        assertTrue(systemPrompt.contains("NEVER claim that the repository itself has no pull requests"),
                "System prompt must forbid claiming repo itself has no PRs");

        // Requirement 5: Explain changes only at level supported by commit messages, do not invent diffs
        assertTrue(systemPrompt.contains("If only commit messages are available, describe changes only at the level supported by those messages"),
                "System prompt must instruct explaining changes only at level of commit messages");
    }

    @Test
    void buildUserPromptContainsAllFiveCommitRecordsWithShasMessagesAndTimestamps() {
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

        String userPrompt = EvidencePromptHelper.buildUserPrompt(evidence);
        assertNotNull(userPrompt);

        // Requirement 2: Ensure LLM receives actual retrieved evidence text
        assertTrue(userPrompt.contains("Rahulram240/AI-Insurance-Review"), "User prompt must contain repository name");
        assertTrue(userPrompt.contains("6039173"), "User prompt must contain commit 1 SHA");
        assertTrue(userPrompt.contains("feat: initial policy review parser"), "User prompt must contain commit 1 message");
        assertTrue(userPrompt.contains("2026-10-15T10:00:00Z"), "User prompt must contain commit 1 date");

        assertTrue(userPrompt.contains("e7e91cc"), "User prompt must contain commit 2 SHA");
        assertTrue(userPrompt.contains("fix: null handling in claim assessment"), "User prompt must contain commit 2 message");

        assertTrue(userPrompt.contains("80b0cdc"), "User prompt must contain commit 3 SHA");
        assertTrue(userPrompt.contains("test: add test suite for risk validation"), "User prompt must contain commit 3 message");

        assertTrue(userPrompt.contains("e780ab7"), "User prompt must contain commit 4 SHA");
        assertTrue(userPrompt.contains("docs: update insurance underwriting guide"), "User prompt must contain commit 4 message");

        assertTrue(userPrompt.contains("9df2845"), "User prompt must contain commit 5 SHA");
        assertTrue(userPrompt.contains("chore: release v1.0.0"), "User prompt must contain commit 5 message");
    }

    @Test
    void extractAndValidateCitationsPreservesGitHubCommitSourceIds() {
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

        String answerText = "Imported commits for Rahulram240/AI-Insurance-Review include [github:commit:Rahulram240/AI-Insurance-Review@6039173] and [github:commit:Rahulram240/AI-Insurance-Review@e7e91cc]. No pull requests were found in the retrieved evidence.";
        List<RetrievedPassage> citations = EvidencePromptHelper.extractAndValidateCitations(answerText, evidence);

        assertEquals(2, citations.size(), "Should accurately extract the two valid cited commit passages");
        assertTrue(citations.stream().anyMatch(c -> c.sourceId().contains("6039173")));
        assertTrue(citations.stream().anyMatch(c -> c.sourceId().contains("e7e91cc")));
        assertFalse(citations.stream().anyMatch(c -> c.sourceId().contains("80b0cdc")));
    }
}
