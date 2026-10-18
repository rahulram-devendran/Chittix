package com.companybrain.service;

import com.companybrain.config.LocalLlmProperties;
import com.companybrain.dto.DecisionContext;
import com.companybrain.dto.Evidence;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.RetrievedPassage;
import com.companybrain.provider.EvidencePromptHelper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Local LLM implementation of AnswerGenerationService using a local Ollama HTTP
 * API.
 * Includes strict evidence grounding, prompt injection guards, citation
 * validation,
 * and robust deterministic fallback when the local model runtime is
 * unavailable.
 */
public class LocalLlmAnswerGenerationService implements AnswerGenerationService {

    private static final Logger log = LoggerFactory.getLogger(LocalLlmAnswerGenerationService.class);

    private final LocalLlmProperties properties;

    private final RestClient restClient;

    public LocalLlmAnswerGenerationService(LocalLlmProperties properties) {
        this(properties, buildDefaultRestClient(properties));
    }

    public LocalLlmAnswerGenerationService(LocalLlmProperties properties, RestClient restClient) {
        this.properties = properties;
        this.restClient = restClient;
    }

    private static RestClient buildDefaultRestClient(LocalLlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.max(1, properties.getTimeoutSeconds()) * 1000;
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMillis, 5000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence package must not be null");
        }

        // Rule: If no evidence is available, do not ask the model to guess
        if (!evidence.hasAnyEvidence()) {
            return new GeneratedAnswer(
                    evidence.question(),
                    "Insufficient evidence found in the company graph or indexed documentation to answer this question.",
                    "INSUFFICIENT_EVIDENCE",
                    List.of(),
                    List.of(),
                    List.of(),
                    EvidenceStatus.INSUFFICIENT,
                    evidence.limitations(),
                    false,
                    null);
        }

        EvidenceStatus status = determineEvidenceStatus(evidence);

        if (isApprovalDateQuestion(evidence.question())
                && !hasExplicitApprovalDateEvidence(evidence)) {

            String answer = "The exact date when the decision was formally approved "
                    + "is not established by the retrieved evidence. A SUPPORTED_BY "
                    + "relationship to a document does not establish formal approval "
                    + "or an approval date.";

            log.info("Returning evidence-limited answer for approval-date question.");

            return new GeneratedAnswer(
                    evidence.question(),
                    answer,
                    "INSUFFICIENT_EVIDENCE",
                    evidence.graphFacts(),
                    evidence.retrievedPassages(),
                    evidence.distinctSources(),
                    EvidenceStatus.INSUFFICIENT,
                    evidence.limitations(),
                    false,
                    null);
        }

        // Fallback if local LLM is explicitly disabled in config
        if (!properties.isEnabled()) {
            log.info(
                    "Local LLM is disabled by configuration (chittix.llm.enabled=false). Using deterministic fallback.");
            return buildDeterministicFallback(evidence, status, "Local LLM disabled by configuration");
        }

        // Attempt local LLM inference via Ollama HTTP API
        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(evidence);

            Map<String, Object> requestBody = Map.of(
                    "model", properties.getModel(),
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "stream", false,
                    "options", Map.of(
                            "temperature", properties.getTemperature(),
                            "num_predict", properties.getMaxTokens()));

            String targetUrl = properties.getBaseUrl().replaceAll("/+$", "") + "/api/chat";
            log.debug("Calling local LLM endpoint '{}' with model '{}'", targetUrl, properties.getModel());

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri(targetUrl)
                    .body(requestBody)
                    .retrieve()
                    .body(Map.class);

            if (response == null) {
                log.warn("Local LLM returned null HTTP response payload.");
                return buildDeterministicFallback(evidence, status, "Local LLM returned empty response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            String rawContent = message != null ? (String) message.get("content") : null;

            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Local LLM produced blank answer text.");
                return buildDeterministicFallback(evidence, status, "Local LLM returned blank content");
            }

            // Post-process citations: validate that any cited sources actually exist in the
            // evidence package
            List<RetrievedPassage> validatedCitations = extractAndValidateCitations(rawContent, evidence);

            log.info("Successfully generated evidence-grounded answer using local LLM ({})", properties.getModel());

            return new GeneratedAnswer(
                    evidence.question(),
                    rawContent.trim(),
                    "LLM_GROUNDED",
                    evidence.graphFacts(),
                    validatedCitations,
                    evidence.distinctSources(),
                    status,
                    evidence.limitations(),
                    false,
                    null);

        } catch (Exception e) {
            log.warn("Local LLM call failed ({}). Activating deterministic fallback.", e.getMessage());
            return buildDeterministicFallback(evidence, status, "Local LLM runtime error: " + e.getMessage());
        }
    }

    private String buildSystemPrompt() {
        return EvidencePromptHelper.buildSystemPrompt();
    }

    private String buildUserPrompt(EvidencePackage evidence) {
        return EvidencePromptHelper.buildUserPrompt(evidence);
    }

    private List<RetrievedPassage> extractAndValidateCitations(String answerText, EvidencePackage evidence) {
        return EvidencePromptHelper.extractAndValidateCitations(answerText, evidence);
    }

    private GeneratedAnswer buildDeterministicFallback(EvidencePackage evidence, EvidenceStatus status, String reason) {
        List<String> sentences = new ArrayList<>();

        if (evidence.hasGraphContext()) {
            for (DecisionContext c : evidence.decisionContexts()) {
                sentences.add("The company graph records the decision \"" + c.decision()
                        + "\" affecting the " + c.project() + " project, which uses " + c.system()
                        + ". It is supported by \"" + c.documentTitle() + "\" (" + c.sourceId() + ").");
            }
        }

        if (evidence.hasDocumentEvidence()) {
            for (RetrievedPassage p : evidence.retrievedPassages()) {
                sentences.add("Relevant documentation from \"" + p.documentTitle() + "\" (" + p.sourceId()
                        + ") states: \"" + p.text() + "\"");
            }
        } else if (evidence.hasGraphContext()) {
            sentences
                    .add("The knowledge base holds no written rationale passages beyond the supporting document link.");
        }

        String fallbackText = "Deterministic fallback (local LLM unavailable): " + String.join(" ", sentences);

        return new GeneratedAnswer(
                evidence.question(),
                fallbackText,
                "DETERMINISTIC_FALLBACK",
                evidence.graphFacts(),
                evidence.retrievedPassages(),
                evidence.distinctSources(),
                status,
                evidence.limitations(),
                true,
                reason);
    }

    private EvidenceStatus determineEvidenceStatus(EvidencePackage evidence) {
        return EvidencePromptHelper.determineEvidenceStatus(evidence);
    }

    private boolean isApprovalDateQuestion(String question) {
        if (question == null) {
            return false;
        }

        String normalized = question.toLowerCase(java.util.Locale.ROOT);

        boolean asksForApproval = normalized.contains("approval")
                || normalized.contains("approved")
                || normalized.contains("ratification")
                || normalized.contains("ratified");

        boolean asksForDate = normalized.contains("date")
                || normalized.contains("when")
                || normalized.contains("exact day");

        return asksForApproval && asksForDate;
    }

    private boolean hasExplicitApprovalDateEvidence(EvidencePackage evidence) {
        Pattern datePattern = Pattern.compile(
                "\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");

        Pattern approvalPattern = Pattern.compile(
                "\\b(?:formally\\s+approved|approved\\s+on|approval\\s+date|date\\s+of\\s+approval|ratified\\s+on)\\b",
                Pattern.CASE_INSENSITIVE);

        for (RetrievedPassage passage : evidence.retrievedPassages()) {
            String text = passage.text();

            if (text == null) {
                continue;
            }

            if (approvalPattern.matcher(text).find()
                    && datePattern.matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

}
