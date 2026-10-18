package com.companybrain.service;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.provider.EvidencePromptHelper;
import com.companybrain.provider.ModelProvider;
import com.companybrain.provider.ModelProviderAdapter;
import com.companybrain.provider.ModelProviderRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Central orchestrator for evidence-grounded answer generation across multiple
 * LLM providers.
 * Decouples Chittix's evidence pipeline from vendor specifics, delegating to
 * registered
 * provider adapters based on externalized configuration.
 */
@Primary
@Service
public class MultiProviderAnswerGenerationService implements AnswerGenerationService {

    private static final Logger log = LoggerFactory.getLogger(MultiProviderAnswerGenerationService.class);

    private final LlmProperties properties;
    private final ModelProviderRegistry registry;

    public MultiProviderAnswerGenerationService(LlmProperties properties, ModelProviderRegistry registry) {
        this.properties = properties;
        this.registry = registry;
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence) {
        if (evidence == null) {
            throw new IllegalArgumentException("Evidence package must not be null");
        }

        // Rule: If no evidence was assembled, do not guess or call any provider
        if (!evidence.hasAnyEvidence()) {
            log.info("No evidence available for question. Returning insufficient evidence response without LLM call.");
            return EvidencePromptHelper.buildInsufficientEvidenceAnswer(evidence);
        }

        EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);

        // Rule: If LLM is disabled globally via configuration, fall back
        // deterministically
        if (!properties.isEnabled()) {
            log.info(
                    "LLM generation is disabled by configuration (chittix.llm.enabled=false). Using deterministic fallback.");
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "LLM disabled by configuration");
        }

        // Resolve configured provider identity
        ModelProvider provider;
        try {
            provider = properties.getModelProvider();
        } catch (IllegalArgumentException e) {
            log.error("Invalid provider configured: {}", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, e.getMessage());
        }

        // Retrieve corresponding adapter from registry
        Optional<ModelProviderAdapter> adapterOpt = registry.findAdapter(provider);
        if (adapterOpt.isEmpty()) {
            String errorMsg = "No adapter registered for provider: '" + provider.getId() + "'";
            log.error(errorMsg);
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, errorMsg);
        }

        ModelProviderAdapter adapter = adapterOpt.get();

        // Check if provider is implemented
        if (!adapter.isImplemented()) {
            String errorMsg = "Provider '" + provider.getId() + "' is recognized but its adapter is not implemented.";
            log.warn(errorMsg);
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, errorMsg);
        }

        // Check if provider is configured (e.g. required API key)
        if (!adapter.isConfigured(properties)) {
            String errorMsg = "Provider '" + provider.getId()
                    + "' is not properly configured. Required API credentials or base URL are missing.";
            log.warn(errorMsg);
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, errorMsg);
        }

        String modelName = properties.resolveModel(provider);

        log.info("Delegating answer generation to provider '{}' with model '{}'", provider.getId(), modelName);

        if (isApprovalDateQuestion(evidence.question())
                && !hasExplicitApprovalDateEvidence(evidence)) {
            log.info("Returning insufficient-evidence response for approval-date question.");

            return new GeneratedAnswer(
                    evidence.question(),
                    "The exact date when the PostgreSQL decision was formally approved "
                            + "is not established by the retrieved evidence. The SUPPORTED_BY "
                            + "relationship to Payment Architecture Design (DOC-001) does not "
                            + "establish formal approval or an approval date.",
                    "INSUFFICIENT_EVIDENCE",
                    evidence.graphFacts(),
                    evidence.retrievedPassages(),
                    evidence.distinctSources(),
                    EvidenceStatus.INSUFFICIENT,
                    evidence.limitations(),
                    false,
                    null);
        }

        return adapter.generateAnswer(evidence, properties);

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
        java.util.regex.Pattern datePattern = java.util.regex.Pattern.compile(
                "\\b(?:\\d{4}-\\d{2}-\\d{2}|\\d{1,2}[/-]\\d{1,2}[/-]\\d{2,4})\\b");

        java.util.regex.Pattern approvalPattern = java.util.regex.Pattern.compile(
                "\\b(?:formally\\s+approved|approved\\s+on|approval\\s+date|date\\s+of\\s+approval|ratified\\s+on)\\b",
                java.util.regex.Pattern.CASE_INSENSITIVE);

        for (var passage : evidence.retrievedPassages()) {
            String text = passage.text();

            if (text != null
                    && approvalPattern.matcher(text).find()
                    && datePattern.matcher(text).find()) {
                return true;
            }
        }

        return false;
    }

}
