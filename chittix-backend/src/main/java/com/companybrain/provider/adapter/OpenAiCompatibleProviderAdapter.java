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
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Provider adapter for any OpenAI-compatible HTTP API (e.g. vLLM, LocalAI, LM Studio, Ollama /v1, Groq, Mistral).
 */
@Component
public class OpenAiCompatibleProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(OpenAiCompatibleProviderAdapter.class);

    private final RestClient customRestClient;

    public OpenAiCompatibleProviderAdapter() {
        this.customRestClient = null;
    }

    public OpenAiCompatibleProviderAdapter(RestClient restClient) {
        this.customRestClient = restClient;
    }

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.OPENAI_COMPATIBLE;
    }

    @Override
    public Set<ModelCapability> getSupportedCapabilities() {
        return ModelProvider.OPENAI_COMPATIBLE.getCapabilities();
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public boolean isConfigured(LlmProperties properties) {
        String baseUrl = properties.resolveBaseUrl(getProvider());
        return baseUrl != null && !baseUrl.isBlank();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
        return generateAnswer(evidence, properties, getProvider());
    }

    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties, ModelProvider targetProvider) {
        EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);
        ModelProvider provider = targetProvider != null ? targetProvider : getProvider();
        String baseUrl = properties.resolveBaseUrl(provider);
        String targetUrl = baseUrl.replaceAll("/+$", "") + "/chat/completions";
        String apiKey = properties.resolveApiKey(provider);
        String modelName = properties.resolveModel(provider);

        try {
            String systemPrompt = EvidencePromptHelper.buildSystemPrompt();
            String userPrompt = EvidencePromptHelper.buildUserPrompt(evidence);

            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "messages", List.of(
                            Map.of("role", "system", "content", systemPrompt),
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "temperature", properties.getTemperature(),
                    "max_tokens", properties.getMaxTokens(),
                    "stream", false
            );

            RestClient client = customRestClient != null ? customRestClient : buildDefaultRestClient(properties);

            RestClient.RequestBodySpec requestSpec = client.post()
                    .uri(targetUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody);

            if (apiKey != null && !apiKey.isBlank()) {
                requestSpec.header("Authorization", "Bearer " + apiKey.trim());
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = requestSpec
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 401) {
                            throw new HttpClientErrorException(HttpStatusCode.valueOf(401),
                                    "Provider returned 401 Unauthorized: Invalid or missing API key");
                        } else if (code == 429) {
                            throw new HttpClientErrorException(HttpStatusCode.valueOf(429),
                                    "Provider rate limit exceeded (HTTP 429)");
                        }
                        throw new HttpClientErrorException(resp.getStatusCode(), "Client error: " + resp.getStatusText());
                    })
                    .body(Map.class);

            if (response == null) {
                log.warn("OpenAI-compatible provider returned null HTTP response payload.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Provider returned empty response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> choices = (List<Map<String, Object>>) response.get("choices");
            if (choices == null || choices.isEmpty()) {
                log.warn("OpenAI-compatible provider returned response with no choices.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Provider returned no choices");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> message = (Map<String, Object>) choices.get(0).get("message");
            String rawContent = message != null ? (String) message.get("content") : null;

            if (rawContent == null || rawContent.isBlank()) {
                log.warn("OpenAI-compatible provider returned blank content.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Provider returned blank content");
            }

            List<RetrievedPassage> validatedCitations = EvidencePromptHelper.extractAndValidateCitations(rawContent, evidence);
            log.info("Successfully generated grounded answer using OpenAI-compatible provider ({})", properties.getModel());

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
                    provider.getId()
            );

        } catch (HttpClientErrorException e) {
            log.warn("OpenAI-compatible provider client error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, e.getMessage());
        } catch (HttpServerErrorException e) {
            log.warn("OpenAI-compatible provider server error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Provider server error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("OpenAI-compatible provider call failed ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Provider runtime error: " + e.getMessage());
        }
    }

    public RestClient getCustomRestClient() {
        return customRestClient;
    }

    @Override
    public com.companybrain.dto.ProviderTestResult testConnection(LlmProperties properties) {
        long start = System.currentTimeMillis();
        String baseUrl = properties.resolveBaseUrl(getProvider());
        String targetUrl = baseUrl.replaceAll("/+$", "") + "/models";
        String apiKey = properties.resolveApiKey(getProvider());
        String modelName = properties.resolveModel(getProvider());

        try {
            RestClient client = customRestClient != null ? customRestClient : buildShortTimeoutRestClient();

            RestClient.RequestHeadersSpec<?> req = client.get()
                    .uri(targetUrl)
                    .accept(MediaType.APPLICATION_JSON);

            if (apiKey != null && !apiKey.isBlank()) {
                req.header("Authorization", "Bearer " + apiKey.trim());
            }

            req.retrieve().toBodilessEntity();

            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                    "Successfully connected to OpenAI-compatible endpoint at " + baseUrl + ".", latency);
        } catch (HttpClientErrorException.Unauthorized | HttpClientErrorException.Forbidden e) {
            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Authentication failed: Invalid or unauthorized API key.", latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            String safeMsg = e.getMessage() != null ? e.getMessage() : "Connection failed";
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Failed to connect to endpoint at " + baseUrl + ": " + safeMsg, latency);
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
