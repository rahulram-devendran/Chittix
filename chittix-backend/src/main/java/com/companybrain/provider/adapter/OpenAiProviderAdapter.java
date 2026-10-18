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

        return delegate.generateAnswer(evidence, properties, ModelProvider.OPENAI);
    }

    @Override
    public com.companybrain.dto.ProviderTestResult testConnection(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.OPENAI);
        String modelName = properties.resolveModel(ModelProvider.OPENAI);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.OPENAI);

        if (apiKey == null || apiKey.isBlank()) {
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "OpenAI requires an API key. Please provide a valid OpenAI API key.", 0L);
        }

        long start = System.currentTimeMillis();
        String targetUrl = baseUrl.replaceAll("/+$", "") + "/models";

        try {
            RestClient client = delegate.getCustomRestClient() != null ? delegate.getCustomRestClient() : buildShortTimeoutRestClient();

            client.get()
                    .uri(targetUrl)
                    .header("Authorization", "Bearer " + apiKey.trim())
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();

            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                    "Successfully connected to OpenAI API. Validated credentials.", latency);
        } catch (org.springframework.web.client.HttpClientErrorException e) {
            long latency = System.currentTimeMillis() - start;
            if (e.getStatusCode().value() == 401) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "OpenAI authentication failed: Invalid API key.", latency);
            } else if (e.getStatusCode().value() == 429) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "OpenAI quota or rate limit exceeded.", latency);
            }
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "OpenAI API returned HTTP " + e.getStatusCode().value(), latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Could not reach OpenAI API: " + e.getMessage(), latency);
        }
    }

    private static RestClient buildShortTimeoutRestClient() {
        org.springframework.http.client.SimpleClientHttpRequestFactory factory = new org.springframework.http.client.SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(java.time.Duration.ofMillis(4000));
        factory.setReadTimeout(java.time.Duration.ofMillis(5000));
        return RestClient.builder().requestFactory(factory).build();
    }
}
