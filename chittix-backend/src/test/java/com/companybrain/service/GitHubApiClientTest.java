package com.companybrain.service;

import com.companybrain.config.GitHubProperties;
import com.companybrain.dto.github.GitHubCommitDto;
import com.companybrain.dto.github.GitHubPullRequestDto;
import com.companybrain.dto.github.GitHubRepoDto;
import com.companybrain.dto.github.GitHubStatusResponse;
import com.companybrain.exception.GitHubApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.net.http.HttpClient;
import java.net.http.HttpHeaders;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GitHubApiClientTest {

    @Mock
    HttpClient httpClient;

    ObjectMapper objectMapper = new ObjectMapper();
    GitHubProperties properties;
    GitHubApiClient client;

    @BeforeEach
    void setUp() {
        properties = new GitHubProperties();
        properties.setToken("ghp_test_token");
        client = new GitHubApiClient(properties, objectMapper, httpClient);
    }

    @Test
    void verifyAccessReturnsUnconfiguredWhenTokenBlank() {
        properties.setToken("");
        GitHubStatusResponse status = client.verifyAccess();

        assertFalse(status.configured());
        assertTrue(status.message().contains("not configured"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAccessParsesSuccessfulUser() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"login\":\"dev-lead\",\"name\":\"Lead Developer\",\"avatar_url\":\"https://github.com/avatar.png\"}");

        HttpHeaders headers = HttpHeaders.of(Map.of(
                "x-oauth-scopes", List.of("repo, read:org"),
                "x-ratelimit-limit", List.of("5000"),
                "x-ratelimit-remaining", List.of("4980"),
                "x-ratelimit-reset", List.of("1700000000")
        ), (k, v) -> true);
        when(response.headers()).thenReturn(headers);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GitHubStatusResponse status = client.verifyAccess();

        assertTrue(status.configured());
        assertEquals("dev-lead", status.username());
        assertEquals("Lead Developer", status.name());
        assertEquals(2, status.scopes().size());
        assertEquals(4980, status.rateLimitRemaining());
    }

    @Test
    @SuppressWarnings("unchecked")
    void verifyAccessHandlesUnauthorized401() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GitHubStatusResponse status = client.verifyAccess();
        assertFalse(status.configured());
        assertTrue(status.message().contains("401"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void listRepositoriesReturnsParsedRepos() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                [
                  {
                    "id": 9991,
                    "name": "chittix-core",
                    "full_name": "company/chittix-core",
                    "description": "Core intelligence engine",
                    "html_url": "https://github.com/company/chittix-core",
                    "default_branch": "main",
                    "private": false,
                    "stargazers_count": 10,
                    "forks_count": 2,
                    "open_issues_count": 1,
                    "updated_at": "2026-10-17T12:00:00Z"
                  }
                ]
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        List<GitHubRepoDto> repos = client.listRepositories();
        assertEquals(1, repos.size());
        assertEquals("chittix-core", repos.get(0).name());
        assertEquals("company/chittix-core", repos.get(0).fullName());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchCommitsReturnsParsedCommits() throws Exception {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("""
                [
                  {
                    "sha": "abcdef1234567890",
                    "html_url": "https://github.com/company/chittix-core/commit/abcdef1",
                    "commit": {
                      "message": "feat: add graph indexing",
                      "author": {
                        "name": "Alice",
                        "email": "alice@example.com",
                        "date": "2026-10-16T15:00:00Z"
                      }
                    }
                  }
                ]
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        List<GitHubCommitDto> commits = client.fetchCommits("company", "chittix-core", 20);
        assertEquals(1, commits.size());
        assertEquals("abcdef1234567890", commits.get(0).sha());
        assertEquals("Alice", commits.get(0).authorName());
        assertEquals("feat: add graph indexing", commits.get(0).message());
    }

    @Test
    @SuppressWarnings("unchecked")
    void fetchPullRequestsReturnsParsedPullRequests() throws Exception {
        HttpResponse<String> prResponse = mock(HttpResponse.class);
        when(prResponse.statusCode()).thenReturn(200);
        when(prResponse.body()).thenReturn("""
                [
                  {
                    "number": 42,
                    "title": "Introduce vector retrieval",
                    "body": "Implements all-MiniLM-L6-v2 embeddings",
                    "state": "closed",
                    "user": { "login": "bob" },
                    "created_at": "2026-10-15T10:00:00Z",
                    "updated_at": "2026-10-16T12:00:00Z",
                    "merged_at": "2026-10-16T12:00:00Z",
                    "html_url": "https://github.com/company/chittix-core/pull/42"
                  }
                ]
                """);

        HttpResponse<String> reviewsResponse = mock(HttpResponse.class);
        when(reviewsResponse.statusCode()).thenReturn(200);
        when(reviewsResponse.body()).thenReturn("""
                [
                  { "user": { "login": "charlie" }, "state": "APPROVED" }
                ]
                """);

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(prResponse)
                .thenReturn(reviewsResponse);

        List<GitHubPullRequestDto> prs = client.fetchPullRequests("company", "chittix-core", 20);
        assertEquals(1, prs.size());
        assertEquals(42, prs.get(0).number());
        assertEquals("Introduce vector retrieval", prs.get(0).title());
        assertEquals("bob", prs.get(0).authorLogin());
        assertTrue(prs.get(0).reviewSummary().contains("charlie (APPROVED)"));
    }

    @Test
    void unconfiguredClientThrowsApiExceptionOnList() {
        properties.setToken("");
        assertThrows(GitHubApiException.class, () -> client.listRepositories());
    }

    @Test
    @SuppressWarnings("unchecked")
    void configureTokenAppliesTokenOnSuccess() throws Exception {
        properties.setToken("ghp_initial");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(200);
        when(response.body()).thenReturn("{\"login\":\"octocat\",\"name\":\"The Octocat\",\"avatar_url\":\"https://github.com/avatar.png\"}");
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(
                "x-oauth-scopes", List.of("repo"),
                "x-ratelimit-limit", List.of("5000"),
                "x-ratelimit-remaining", List.of("4999")
        ), (k, v) -> true));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        GitHubStatusResponse result = client.configureToken("ghp_new_valid_token");

        assertTrue(result.configured());
        assertEquals("octocat", result.username());
        assertEquals("ghp_new_valid_token", properties.getToken());
    }

    @Test
    @SuppressWarnings("unchecked")
    void configureTokenDoesNotOverwriteTokenOnFailure() throws Exception {
        properties.setToken("ghp_working_token");

        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(401);
        when(response.headers()).thenReturn(HttpHeaders.of(Map.of(), (k, v) -> true));

        when(httpClient.send(any(HttpRequest.class), any(HttpResponse.BodyHandler.class)))
                .thenReturn(response);

        org.springframework.web.server.ResponseStatusException ex = assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> client.configureToken("ghp_bad_token")
        );

        assertTrue(ex.getMessage().contains("validation failed"));
        // Working token MUST NOT be overwritten on failure
        assertEquals("ghp_working_token", properties.getToken());
    }

    @Test
    void configureTokenThrowsOnBlankTokenAndPreservesExisting() {
        properties.setToken("ghp_working_token");

        assertThrows(
                org.springframework.web.server.ResponseStatusException.class,
                () -> client.configureToken("   ")
        );

        assertEquals("ghp_working_token", properties.getToken());
    }
}
