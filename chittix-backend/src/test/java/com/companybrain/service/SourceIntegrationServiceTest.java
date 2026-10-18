package com.companybrain.service;

import com.companybrain.dto.source.SourceConfigRequest;
import com.companybrain.dto.source.SourceItemDto;
import com.companybrain.dto.source.SourceStatusResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SourceIntegrationServiceTest {

    @Mock
    HttpClient httpClient;

    ObjectMapper objectMapper = new ObjectMapper();
    SourceIntegrationService service;

    @BeforeEach
    void setUp() {
        service = new SourceIntegrationService(objectMapper, httpClient);
    }

    @Test
    void testGitLabConnectionSuccessWithUserEndpoint() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("{\"username\":\"gituser\",\"name\":\"Git User\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResp);

        SourceStatusResponse resp = service.testConnection("gitlab", "glpat-secret", "", "https://gitlab.com");
        assertTrue(resp.connected());
        assertEquals("gituser", resp.username());
    }

    @Test
    void testGitLabConnectionFixes404WithProjectTokenFallback() throws Exception {
        HttpResponse<String> userResp = mock(HttpResponse.class);
        when(userResp.statusCode()).thenReturn(404);

        HttpResponse<String> projResp = mock(HttpResponse.class);
        when(projResp.statusCode()).thenReturn(200);

        // First call to /user returns 404, fallback to /projects returns 200
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(userResp)
                .thenReturn(userResp)
                .thenReturn(projResp);

        SourceStatusResponse resp = service.testConnection("gitlab", "glpat-project-token", "", "https://gitlab.com/api/v4/");
        assertTrue(resp.connected());
        assertEquals("gitlab-token", resp.username());
    }

    @Test
    void testBitbucketConnectionSuccess() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("{\"username\":\"bbuser\",\"display_name\":\"Bitbucket User\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResp);

        SourceStatusResponse resp = service.testConnection("bitbucket", "app-password", "bbuser", "https://api.bitbucket.org");
        assertTrue(resp.connected());
        assertEquals("bbuser", resp.username());
    }

    @Test
    void testConfluenceConnectionSuccess() throws Exception {
        HttpResponse<String> mockResp = mock(HttpResponse.class);
        when(mockResp.statusCode()).thenReturn(200);
        when(mockResp.body()).thenReturn("{\"publicName\":\"confluence-user\",\"displayName\":\"Confluence User\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(mockResp);

        SourceStatusResponse resp = service.testConnection("confluence", "api-token", "user@company.com", "https://company.atlassian.net");
        assertTrue(resp.connected());
        assertEquals("confluence-user", resp.username());
    }

    @Test
    void configureSourcePreservesPreviousWorkingCredentialsOnFailure() throws Exception {
        HttpResponse<String> successResp = mock(HttpResponse.class);
        when(successResp.statusCode()).thenReturn(200);
        when(successResp.body()).thenReturn("{\"username\":\"gituser\",\"name\":\"Git User\"}");

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(successResp);

        // 1. Initial successful configuration
        SourceStatusResponse initial = service.configureSource(
                new SourceConfigRequest("gitlab", "glpat-valid", "", "https://gitlab.com")
        );
        assertTrue(initial.connected());
        assertTrue(service.isConfigured("gitlab"));

        // 2. Candidate invalid configuration
        HttpResponse<String> failResp = mock(HttpResponse.class);
        when(failResp.statusCode()).thenReturn(401);
        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(failResp);

        assertThrows(ResponseStatusException.class, () -> service.configureSource(
                new SourceConfigRequest("gitlab", "glpat-invalid", "", "https://gitlab.com")
        ));

        // 3. Verify previous valid credentials are still preserved in memory
        assertTrue(service.isConfigured("gitlab"));
        assertEquals("glpat-valid", service.getCredential("gitlab").token());
    }

    @Test
    void listItemsReturnsDiscoveredGitLabProjects() throws Exception {
        HttpResponse<String> userResp = mock(HttpResponse.class);
        when(userResp.statusCode()).thenReturn(200);
        when(userResp.body()).thenReturn("{\"username\":\"gituser\"}");

        HttpResponse<String> listResp = mock(HttpResponse.class);
        when(listResp.statusCode()).thenReturn(200);
        when(listResp.body()).thenReturn("""
                [
                  {
                    "id": 42,
                    "name": "ai-review",
                    "path_with_namespace": "myorg/ai-review",
                    "description": "AI Code review tool",
                    "web_url": "https://gitlab.com/myorg/ai-review",
                    "default_branch": "main",
                    "visibility": "public"
                  }
                ]
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(userResp)
                .thenReturn(listResp);

        service.configureSource(new SourceConfigRequest("gitlab", "glpat-token", "", "https://gitlab.com"));

        List<SourceItemDto> items = service.listItems("gitlab");
        assertEquals(1, items.size());
        assertEquals("myorg/ai-review", items.get(0).fullName());
        assertFalse(items.get(0).isPrivate());
    }
}
