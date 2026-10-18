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
 * Native adapter for Anthropic Claude API (/v1/messages format).
 */
@Component
public class AnthropicProviderAdapter implements ModelProviderAdapter {

    private static final Logger log = LoggerFactory.getLogger(AnthropicProviderAdapter.class);

    private final RestClient customRestClient;

    public AnthropicProviderAdapter() {
        this.customRestClient = null;
    }

    public AnthropicProviderAdapter(RestClient restClient) {
        this.customRestClient = restClient;
    }

    @Override
    public ModelProvider getProvider() {
        return ModelProvider.ANTHROPIC;
    }

    @Override
    public Set<ModelCapability> getSupportedCapabilities() {
        return ModelProvider.ANTHROPIC.getCapabilities();
    }

    @Override
    public boolean isImplemented() {
        return true;
    }

    @Override
    public boolean isConfigured(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.ANTHROPIC);
        return apiKey != null && !apiKey.isBlank();
    }

    @Override
    public GeneratedAnswer generateAnswer(EvidencePackage evidence, LlmProperties properties) {
        EvidenceStatus status = EvidencePromptHelper.determineEvidenceStatus(evidence);

        if (!isConfigured(properties)) {
            return EvidencePromptHelper.buildDeterministicFallback(
                    evidence,
                    status,
                    "Provider 'anthropic' requires an API key (ANTHROPIC_API_KEY or chittix.llm.anthropic-api-key), but none was configured."
            );
        }

        String apiKey = properties.resolveApiKey(ModelProvider.ANTHROPIC).trim();
        String modelName = properties.resolveModel(ModelProvider.ANTHROPIC);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.ANTHROPIC).replaceAll("/+$", "");
        String targetUrl = baseUrl + "/v1/messages";

        try {
            String systemPrompt = EvidencePromptHelper.buildSystemPrompt();
            String userPrompt = EvidencePromptHelper.buildUserPrompt(evidence);

            Map<String, Object> requestBody = Map.of(
                    "model", modelName,
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userPrompt)
                    ),
                    "max_tokens", properties.getMaxTokens(),
                    "temperature", properties.getTemperature()
            );

            RestClient client = customRestClient != null ? customRestClient : buildDefaultRestClient(properties);

            @SuppressWarnings("unchecked")
            Map<String, Object> response = client.post()
                    .uri(targetUrl)
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(requestBody)
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 401) {
                            throw new HttpClientErrorException(HttpStatusCode.valueOf(401),
                                    "Anthropic authentication failed (HTTP 401): Invalid API key");
                        } else if (code == 429) {
                            throw new HttpClientErrorException(HttpStatusCode.valueOf(429),
                                    "Anthropic rate limit exceeded (HTTP 429)");
                        }
                        throw new HttpClientErrorException(resp.getStatusCode(), "Anthropic client error: " + resp.getStatusText());
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, resp) -> {
                        int code = resp.getStatusCode().value();
                        if (code == 529) {
                            throw new HttpServerErrorException(HttpStatusCode.valueOf(529),
                                    "Anthropic API is overloaded (HTTP 529)");
                        }
                        throw new HttpServerErrorException(resp.getStatusCode(), "Anthropic server error: " + resp.getStatusText());
                    })
                    .body(Map.class);

            if (response == null) {
                log.warn("Anthropic returned null HTTP response payload.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Anthropic returned empty response");
            }

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> contentList = (List<Map<String, Object>>) response.get("content");
            if (contentList == null || contentList.isEmpty()) {
                log.warn("Anthropic returned response with no content blocks.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Anthropic returned empty content");
            }

            String rawContent = null;
            for (Map<String, Object> block : contentList) {
                if ("text".equals(block.get("type"))) {
                    rawContent = (String) block.get("text");
                    break;
                }
            }

            if (rawContent == null || rawContent.isBlank()) {
                log.warn("Anthropic returned blank text content.");
                return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Anthropic returned blank content");
            }

            List<RetrievedPassage> validatedCitations = EvidencePromptHelper.extractAndValidateCitations(rawContent, evidence);
            log.info("Successfully generated grounded answer using Anthropic ({})", modelName);

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
                    null
            );

        } catch (HttpClientErrorException e) {
            log.warn("Anthropic provider client error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, e.getMessage());
        } catch (HttpServerErrorException e) {
            log.warn("Anthropic provider server error ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, e.getMessage());
        } catch (Exception e) {
            log.warn("Anthropic provider call failed ({}). Activating deterministic fallback.", e.getMessage());
            return EvidencePromptHelper.buildDeterministicFallback(evidence, status, "Anthropic runtime error: " + e.getMessage());
        }
    }

    @Override
    public com.companybrain.dto.ProviderTestResult testConnection(LlmProperties properties) {
        String apiKey = properties.resolveApiKey(ModelProvider.ANTHROPIC);
        String modelName = properties.resolveModel(ModelProvider.ANTHROPIC);
        String baseUrl = properties.resolveBaseUrl(ModelProvider.ANTHROPIC).replaceAll("/+$", "");

        if (apiKey == null || apiKey.isBlank()) {
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Anthropic requires an API key. Please provide a valid Anthropic API key.", 0L);
        }

        long start = System.currentTimeMillis();
        String targetUrl = baseUrl + "/v1/models";

        try {
            RestClient client = customRestClient != null ? customRestClient : buildShortTimeoutRestClient();

            client.get()
                    .uri(targetUrl)
                    .header("x-api-key", apiKey.trim())
                    .header("anthropic-version", "2023-06-01")
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .toBodilessEntity();

            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(true, getProvider().getId(), modelName,
                    "Successfully connected to Anthropic API. Validated credentials.", latency);
        } catch (HttpClientErrorException e) {
            long latency = System.currentTimeMillis() - start;
            int code = e.getStatusCode().value();
            if (code == 401) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "Anthropic authentication failed: Invalid API key.", latency);
            } else if (code == 429) {
                return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                        "Anthropic rate limit exceeded.", latency);
            }
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Anthropic API returned HTTP " + code, latency);
        } catch (Exception e) {
            long latency = System.currentTimeMillis() - start;
            return new com.companybrain.dto.ProviderTestResult(false, getProvider().getId(), modelName,
                    "Could not reach Anthropic API: " + e.getMessage(), latency);
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
