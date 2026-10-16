package com.companybrain.provider;

import com.companybrain.config.LlmProperties;
import com.companybrain.dto.EvidencePackage;
import com.companybrain.dto.GeneratedAnswer;
import com.companybrain.dto.RetrievedPassage;
import com.companybrain.provider.adapter.OpenAiProviderAdapter;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenAiProviderAdapterTest {

    private final RetrievedPassage realPassage = new RetrievedPassage(
            "DOC-001#chunk-0",
            0,
            "PostgreSQL was chosen for the Payment Service because it guarantees strict ACID transactions.",
            "DOC-001",
            "Payment Architecture Design",
            0.15,
            0.85
    );

    @Test
    void rejectsExecutionWithoutApiKeyAndReturnsDeterministicFallback() {
        LlmProperties properties = new LlmProperties(true, "openai", "gpt-4o-mini", "https://api.openai.com/v1", 15, 0.1, 500);
        properties.setApiKey(""); // Missing API key

        EvidencePackage evidence = new EvidencePackage(
                "Why PostgreSQL?",
                List.of("fact-1"),
                List.of(),
                List.of(realPassage),
                true,
                true,
                true,
                List.of()
        );

        OpenAiProviderAdapter adapter = new OpenAiProviderAdapter(RestClient.builder().build());
        GeneratedAnswer answer = adapter.generateAnswer(evidence, properties);

        assertEquals("DETERMINISTIC_FALLBACK", answer.answerType());
        assertTrue(answer.fallbackUsed());
        assertNotNull(answer.fallbackReason());
        assertTrue(answer.fallbackReason().contains("requires an API key"));
        assertTrue(answer.answerText().contains("Deterministic fallback"));
    }
}
