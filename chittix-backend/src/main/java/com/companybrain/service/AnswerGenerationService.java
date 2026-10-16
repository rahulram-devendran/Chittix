package com.companybrain.service;

import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.GeneratedAnswer;

/**
 * Provider-independent interface for generating natural-language answers
 * grounded strictly in assembled graph and document evidence.
 */
public interface AnswerGenerationService {

    /**
     * Generates a grounded answer from the provided evidence package.
     *
     * @param evidencePackage verified graph context, document passages, and limitations
     * @return GeneratedAnswer containing answer text, verified citations, and metadata
     */
    GeneratedAnswer generateAnswer(EvidencePackage evidencePackage);
}
