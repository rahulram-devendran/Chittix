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
 * Native adapter for Google Gemini API (generateContent format).
 */
@Component
public class GeminiProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(GeminiProviderAdapter.class);

    private final RestClient customRestClient;

    public GeminiProviderAdapter() {
        this.customRestClient = null;
    }

    public GeminiProviderAdapter(RestClient restClient) {
        this.customRestClient = restClient;
    }

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.GEMINI;
    }

    @Override
    public Set<ModelCapability> getSupportedCapabilities() {
        return ModelProvider.GEMINI.getCapabilities();
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public boolean isConfigured(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.GEMINI);
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
        EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);

        if (!isConfigured(properties)) {
            return EvidencePromptHelper.buildDeterministicFallback(
                    evidence,
                    status,
                    "Provider 'gemini' requires an API key (GEMINI_API_KEY or chittix.llm.gemini-api-key), but none was configured."
            );
        }

        String apiKey = properties.resolveApiKey(ModelProvider.GEMINI).trim();
        String modelName = properties.resolveModel(ModelProvider.GEMINI);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.GEMINI).replaceAll("/+$", "");
        String targetUrl = baseUrl + "/v1beta/models/" + modelName + ":generateContent";

        try {
            String systemPrompt = EvidencePromptHelper.buildSystemPrompt();
            String userPrompt = EvidencePromptHelper.buildUserPrompt(evidence);

            Map<String, Object> requestBody = Map.of(
                    "system_instruction", Map.of(
                            "parts", List.of(Map.of("text", systemPrompt))
                    ),
                    "contents", List.of(
                            Map.of(
                                    "role", "user",
                                    "parts", List.of(Map.of("text", userPrompt))
                            )
                    ),
                    "generationConfig", Map.of(
                            "temperature", properties.getTemperature(),
                            "maxOutputTokens", properties.getMaxTokens()
                    )
            );

            RestClient client = customRestClient != null ? customRestClient : buildDefaultRestClient(properties);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(targetUrl)
                    .header("x-goog-api-key", apiKey)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 401 || code == 403) {
                            throw new HttpClientErrorException(resp.getStatusCode(),
                                    "Gemini authentication failed (HTTP " + code + "): Invalid API key or permission denied");
                        } else if (code == 429) {
                            throw new HttpClientErrorException(HttpStatusCode.valueOf(429),
                                    "Gemini quota or rate limit exceeded (HTTP 429)");
                        }
                        throw new HttpClientErrorException(resp.getStatusCode(), "Gemini client error: " + resp.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        throw new HttpServerErrorException(resp.getStatusCode(), "Gemini server error: " + resp.getStatusText());
                    })
                    .body(Map.class);

            if (response == null) {
                log.warn("Gemini returned null HTTP response payload.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini returned empty response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> candidates = (List<Map<String, Object>>) response.get("candidates");
            if (candidates == null || candidates.isEmpty()) {
                log.warn("Gemini returned response with no candidates.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini returned no candidates");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> candidate = candidates.get(0);
            @SuppressWarnings("unchecked")
            Map<String, Object> contentMap = (Map<String, Object>) candidate.get("content");
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> parts = contentMap != null ? (List<Map<String, Object>>) contentMap.get("parts") : null;

            if (parts == null || parts.isEmpty()) {
                log.warn("Gemini returned candidate with no parts.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini returned no content parts");
            }

            String rawContent = (String) parts.get(0).get("text");
            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Gemini returned blank text content.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini returned blank content");
            }

            List<RetrievedPassage> validatedCitations = EvidencePromptHelper.extractAndValidateCitations(rawContent, evidence);
            log.info("Successfully generated grounded answer using Gemini ({})", modelName);

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
                    getProvider().getId()
            );

        } catch (HttpClientErrorException e) {
            log.warn("Gemini provider client error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, e.getMessage());
        } catch (HttpServerErrorException e) {
            log.warn("Gemini provider server error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini server error: " + e.getMessage());
        } catch (Exception e) {
            log.warn("Gemini provider call failed ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Gemini runtime error: " + e.getMessage());
        }
    }

    @Override
    public com.companybrain.dto.ProviderTestResult testConnection(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.GEMINI);
        String modelName = properties.resolveModel(ModelProvider.GEMINI);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.GEMINI).replaceAll("/+$", "");

        if (apiKey == null || apiKey.isBlank()) {
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Google Gemini requires an API key. Please provide a valid Gemini API key.", 0L);
        }

        long start = System.currentTimeMillis();
        String targetUrl = baseUrl + "/v1beta/models";

        try {
            RestClient client = customRestClient != null ? customRestClient : buildShortTimeoutRestClient();

            client.get()
                    .uri(targetUrl)
                    .header("x-goog-api-key", apiKey.trim())
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();

            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                    "Successfully connected to Google Gemini API. Validated credentials.", latency);
        } catch (HttpClientErrorException e) {
            long latency = System.currentTimeMillis() - start;
            int code = e.getStatusCode().value();
            if (code == 400 || code == 401 || code == 403) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "Gemini authentication failed: Invalid API key or permission denied.", latency);
            } else if (code == 429) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "Gemini quota or rate limit exceeded.", latency);
            }
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Gemini API returned HTTP " + code, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Could not reach Google Gemini API: " + e.getMessage(), latency);
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
