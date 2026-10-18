package com.companybrain.controller;

import com.companybrain.dto.AskResponse;
import com.companybrain.dto.GraphDataResponse;
import com.companybrain.dto.GraphNodeDto;
import com.companybrain.dto.GraphRelationshipDto;
import com.companybrain.exception.GlobalExceptionHandler;
import com.companybrain.exception.GraphUnavailableException;
import com.companybrain.service.BrainService;
import com.companybrain.service.ContextQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BrainControllerTest {

    @Mock
    BrainService brainService;

    @Mock
    ContextQueryService contextQueryService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BrainController(brainService, contextQueryService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void returnsAnswerForValidQuestion() throws Exception {
        when(brainService.ask("Why X?")).thenReturn(
                new AskResponse("Why X?", true, "PRELIMINARY", "Preliminary answer: ...",
                        List.of("f1"), List.of(), List.of()));

        mvc.perform(post("/api/brain/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Why X?\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.question").value("Why X?"))
                .andExpect(jsonPath("$.answerType").value("PRELIMINARY"))
                .andExpect(jsonPath("$.facts[0]").value("f1"));
    }

    @Test
    void rejectsBlankQuestion() throws Exception {
        mvc.perform(post("/api/brain/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"   \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("question")));
    }

    @Test
    void rejectsMalformedBody() throws Exception {
        mvc.perform(post("/api/brain/ask").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void returns503WhenGraphUnavailable() throws Exception {
        when(brainService.ask("Why X?")).thenThrow(new GraphUnavailableException("db down", null));

        mvc.perform(post("/api/brain/ask").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"question\":\"Why X?\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value("Knowledge graph service (FalkorDB) is currently unavailable. Please verify the database container is running and retry."));
    }

    @Test
    void graphReturnsTopologyFromContextQueryService() throws Exception {
        when(contextQueryService.getKnowledgeGraph()).thenReturn(new GraphDataResponse(
                "company_brain",
                1,
                1,
                List.of(new GraphNodeDto("1", "Payment Service", "Project", null, java.util.Map.of("name", "Payment Service"))),
                List.of(new GraphRelationshipDto("9", "1", "2", "USES"))));

        mvc.perform(get("/api/brain/graph"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.graph").value("company_brain"))
                .andExpect(jsonPath("$.nodeCount").value(1))
                .andExpect(jsonPath("$.nodes[0].type").value("Project"))
                .andExpect(jsonPath("$.relationships[0].type").value("USES"));
    }

    @Test
    void testProviderReturnsTestResult() throws Exception {
        when(brainService.testProvider(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(new com.companybrain.dto.ProviderTestResult(true, "ollama", "qwen2.5:1.5b", "Connected", 45L));

        mvc.perform(post("/api/brain/test-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"ollama\",\"model\":\"qwen2.5:1.5b\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.provider").value("ollama"))
                .andExpect(jsonPath("$.latencyMs").value(45));
    }

    @Test
    void configureProviderRejectsFailedTest() throws Exception {
        when(brainService.configureProvider(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalArgumentException("Connection test failed: Authentication error"));

        mvc.perform(post("/api/brain/configure-provider")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"provider\":\"openai\",\"apiKey\":\"bad-key\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Connection test failed")));
    }
}
