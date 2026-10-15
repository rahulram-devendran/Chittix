package com.companybrain.controller;

import com.companybrain.dto.AskResponse;
import com.companybrain.exception.GlobalExceptionHandler;
import com.companybrain.exception.GraphUnavailableException;
import com.companybrain.service.BrainService;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class BrainControllerTest {

    @Mock
    BrainService brainService;

    MockMvc mvc;

    @BeforeEach
    void setUp() {
        mvc = MockMvcBuilders.standaloneSetup(new BrainController(brainService))
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
                .andExpect(jsonPath("$.message").value("db down"));
    }
}
