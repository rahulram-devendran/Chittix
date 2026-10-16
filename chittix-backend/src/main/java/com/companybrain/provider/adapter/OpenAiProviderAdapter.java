package com.companybrain.provider.adapter;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.provider.EvidencePromptHelper;
import com.companybrain.provider.ModelCapability;
import com.companybrain.provider.ModelProvider;
import com.companybrain.provider.ModelProviderAdapter;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Set;

/**
 * Dedicated provider adapter for official OpenAI API endpoints.
 * Validates API key configuration before making requests.
 */
@Component
public class OpenAiProviderAdapter implements ModelProviderAdapter {

    private final OpenAiCompatibleProviderAdapter delegate;

    public OpenAiProviderAdapter() {
        this.delegate = new OpenAiCompatibleProviderAdapter();
    }

    public OpenAiProviderAdapter(RestClient restClient) {
        this.delegate = new OpenAiCompatibleProviderAdapter(restClient);
    }

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.OPENAI;
    }

    @Override
    public Set<ModelCapability> getSupportedCapabilities() {
        return ModelProvider.OPENAI.getCapabilities();
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public boolean isConfigured(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.OPENAI);
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
        if (!isConfigured(properties)) {
            EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);
            return EvidencePromptHelper.buildDeterministicFallback(
                    evidence,
                    status,
                    "Provider 'openai' requires an API key (OPENAI_API_KEY or chittix.llm.openai-api-key), but none was configured."
            );
        }

        return delegate.generateAnswer(evidence, properties);
    }
}
