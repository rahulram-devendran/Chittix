package com.companybrain.provider.adapter;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.EvidenceStatus;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.RetrievedPassage;
import com.companybrain.provider.EvidencePromptHelper;
import com.companybrain.provider.ModelCapability;
import com.companybrain.provider.ModelProvider;
import com.companybrain.provider.ModelProviderAdapter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Adapter for Ollama local LLM runtime (₹0 budget, completely local inference).
 */
@Component
public class OllamaProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OllamaProviderAdapter.class);

    private final RestClient customRestClient;

    public OllamaProviderAdapter() {
        this.customRestClient = null;
    }

    public OllamaProviderAdapter(RestClient restClient) {
        this.customRestClient = restClient;
    }

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.OLLAMA;
    }

    @Override
    public Set<ModelCapability> getSupportedCapabilities() {
        return ModelProvider.OLLAMA.getCapabilities();
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public boolean isConfigured(LlmProperties properties) {
        String baseUrl = properties.resolveBaseUrl(ModelProvider.OLLAMA);
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
        EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.OLLAMA);
        String targetUrl = baseUrl.replaceAll("/+$", "") + "/api/chat";
        String modelName = properties.resolveModel(ModelProvider.OLLAMA);

        try {
            String systemPrompt = EvidencePromptHelper.buildSystemPrompt();
            String userPrompt = EvidencePromptHelper.buildUserPrompt(evidence);

            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)),
                    "stream", false,
                    "options", Map.of(
                            "temperature", properties.getTemperature(),
                            "num_predict", properties.getMaxTokens()));

            RestClient client = customRestClient != null ? customRestClient : buildDefaultRestClient(properties);

            String responseBody = client.post()
                    .uri(targetUrl)
                    .contentType(org.springframework.http.MediaType.APPLICATION_JSON)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .exchange((request, response) -> {
                        if (!response.getStatusCode().is2xxSuccessful()) {
                            throw new IllegalStateException(
                                    "Ollama returned HTTP " + response.getStatusCode());
                        }

                        return org.springframework.util.StreamUtils.copyToString(
                                response.getBody(),
                                StandardCharsets.UTF_8);
                    });

            if (responseBody == null || responseBody.isBlank()) {
                log.warn("Ollama returned an empty HTTP response body.");
                return EvidencePromptHelper.buildDeterministicFallback(
                        evidence, status, "Ollama returned empty response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = new com.fasterxml.jackson.databind.ObjectMapper()
                    .readValue(responseBody, Map.class);

            if (response == null) {
                log.warn("Ollama returned null HTTP response payload.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status,
                        "Ollama returned empty response");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) response.get("message");
            String rawContent = message != null ? (String) message.get("content") : null;

            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Ollama produced blank answer text.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status,
                        "Ollama returned blank content");
            }

            List<RetrievedPassage> validatedCitations = EvidencePromptHelper.extractAndValidateCitations(rawContent,
                    evidence);
            log.info("Successfully generated grounded answer using Ollama ({})", properties.getModel());

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
                    null,
                    modelName,
                    getProvider().getId());

        } catch (Exception e) {
            log.warn("Ollama provider call failed ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status,
                    "Ollama runtime error: " + e.getMessage());
        }
    }

    @Override
    public com.companybrain.dto.ProviderTestResult testConnection(LlmProperties properties) {
        long start = System.currentTimeMillis();
        String baseUrl = properties.resolveBaseUrl(ModelProvider.OLLAMA);
        String modelName = properties.resolveModel(ModelProvider.OLLAMA);
        String tagsUrl = baseUrl.replaceAll("/+$", "") + "/api/tags";

        try {
            RestClient client = customRestClient != null ? customRestClient : buildShortTimeoutRestClient();

            String responseBody = client.get()
                    .uri(tagsUrl)
                    .accept(org.springframework.http.MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(String.class);

            long latency = System.currentTimeMillis() - start;

            if (responseBody != null && responseBody.contains("\"" + modelName + "\"")) {
                return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                        "Successfully connected to Ollama at " + baseUrl + ". Model '" + modelName + "' is ready.", latency);
            } else if (responseBody != null && responseBody.contains("models")) {
                return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                        "Connected to Ollama at " + baseUrl + ", but model '" + modelName + "' is not yet downloaded.", latency);
            } else {
                return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                        "Successfully reached Ollama runtime at " + baseUrl + ".", latency);
            }
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String safeMsg = e.getMessage() != null ? e.getMessage() : "Connection refused";
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Failed to connect to Ollama at " + baseUrl + ": " + safeMsg, latency);
        }
    }

    private static RestClient buildShortTimeoutRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(Duration.ofMillis(4000));
        factory.setReadTimeout(Duration.ofMillis(5000));
        return RestClient.builder().requestFactory(factory).build();
    }

    private static RestClient buildDefaultRestClient(LlmProperties properties) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        int timeoutMillis = Math.max(1, properties.getTimeoutSeconds()) * 1000;
        factory.setConnectTimeout(Duration.ofMillis(Math.min(timeoutMillis, 5000)));
        factory.setReadTimeout(Duration.ofMillis(timeoutMillis));

        return RestClient.builder()
                .requestFactory(factory)
                .build();
    }
}
