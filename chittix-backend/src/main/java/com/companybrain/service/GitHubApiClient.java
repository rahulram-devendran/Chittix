package com.companybrain.service;

import com.companybrain.config.GitHubProperties;
import com.companybrain.dto.github.GitHubCommitDto;
import com.companybrain.dto.github.GitHubPullRequestDto;
import com.companybrain.dto.github.GitHubRepoDto;
import com.companybrain.dto.github.GitHubStatusResponse;
import com.companybrain.exception.GitHubApiException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Standard Java 21 HttpClient wrapper for communicating with GitHub REST API.
 * Never logs or returns the GitHub token.
 */
@Component
public class GitHubApiClient {

    private static final Logger log = LoggerFactory.getLogger(GitHubApiClient.class);

    private final GitHubProperties properties;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    @Autowired 
    public GitHubApiClient(GitHubProperties properties, ObjectMapper objectMapper) {
        this(properties, objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public GitHubApiClient(GitHubProperties properties, ObjectMapper objectMapper, HttpClient httpClient) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public GitHubStatusResponse verifyAccess() {
        if (!properties.isConfigured()) {
            return GitHubStatusResponse.unconfigured(
                    "GitHub access token is not configured. Supply GITHUB_TOKEN environment variable to the backend or configure via Sources UI."
            );
        }
        return verifyWithToken(properties.getToken());
    }

    public GitHubStatusResponse verifyToken(String candidateToken) {
        if (candidateToken == null || candidateToken.trim().isEmpty()) {
            return GitHubStatusResponse.unconfigured("GitHub access token is blank or empty.");
        }
        String token = candidateToken.trim();
        if (token.startsWith("Bearer ") || token.startsWith("bearer ")) {
            token = token.substring(7).trim();
        }
        return verifyWithToken(token);
    }

    private GitHubStatusResponse verifyWithToken(String token) {
        HttpRequest request = buildRequest("/user", token);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            int status = response.statusCode();
            List<String> scopes = extractScopes(response);
            int rateLimit = extractHeaderInt(response, "x-ratelimit-limit", 0);
            int rateRemaining = extractHeaderInt(response, "x-ratelimit-remaining", 0);
            long rateReset = extractHeaderLong(response, "x-ratelimit-reset", 0L);

            if (status == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String username = root.path("login").asText();
                String name = root.path("name").asText(username);
                String avatarUrl = root.path("avatar_url").asText("");

                log.info("GitHub access verified successfully for user: {}", username);
                return new GitHubStatusResponse(
                        true, username, name, avatarUrl, scopes,
                        rateLimit, rateRemaining, rateReset,
                        "GitHub connection verified successfully"
                );
            } else if (status == 401) {
                return GitHubStatusResponse.unconfigured("GitHub token authentication failed (HTTP 401 Unauthorized)");
            } else if (status == 403) {
                String message = parseErrorMessage(response.body(), "Rate limit exceeded or permission denied (HTTP 403)");
                return new GitHubStatusResponse(
                        false, null, null, null, scopes,
                        rateLimit, rateRemaining, rateReset,
                        message
                );
            } else {
                String message = parseErrorMessage(response.body(), "GitHub API responded with HTTP " + status);
                return GitHubStatusResponse.unconfigured(message);
            }
        } catch (IOException e) {
            log.error("Network I/O error during GitHub connection verification: {}", e.getMessage());
            return GitHubStatusResponse.unconfigured("Failed to reach GitHub API: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return GitHubStatusResponse.unconfigured("GitHub verification was interrupted");
        }
    }

    /**
     * Validates candidate token against GitHub before activating it in memory.
     * Guarantees that a failed test will never replace the last working configuration.
     */
    public synchronized GitHubStatusResponse configureToken(String candidateToken) {
        if (candidateToken == null || candidateToken.trim().isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GitHub access token must not be blank"
            );
        }
        String trimmed = candidateToken.trim();
        if (trimmed.startsWith("Bearer ") || trimmed.startsWith("bearer ")) {
            trimmed = trimmed.substring(7).trim();
        }
        if (trimmed.isEmpty()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GitHub access token must not be blank"
            );
        }

        GitHubStatusResponse validation = verifyToken(trimmed);
        if (!validation.configured()) {
            throw new org.springframework.web.server.ResponseStatusException(
                    org.springframework.http.HttpStatus.BAD_REQUEST,
                    "GitHub token validation failed: " + validation.message()
            );
        }

        properties.setToken(trimmed);
        return validation;
    }

    public List<GitHubRepoDto> listRepositories() {
        requireConfigured();

        // Fetch up to 100 repositories accessible to the configured identity
        HttpRequest request = buildRequest("/user/repos?per_page=100&sort=updated&affiliation=owner,collaborator,organization_member");
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponseSuccess(response, "Failed to list repositories");

            JsonNode array = objectMapper.readTree(response.body());
            if (!array.isArray()) {
                return Collections.emptyList();
            }

            List<GitHubRepoDto> repos = new ArrayList<>();
            for (JsonNode node : array) {
                repos.add(mapRepoDto(node));
            }
            return repos;
        } catch (IOException e) {
            throw new GitHubApiException(502, "Failed to communicate with GitHub API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(500, "GitHub request was interrupted", e);
        }
    }

    public GitHubRepoDto fetchRepository(String owner, String repo) {
        requireConfigured();

        HttpRequest request = buildRequest("/repos/" + owner + "/" + repo);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            checkResponseSuccess(response, "Failed to fetch repository " + owner + "/" + repo);

            JsonNode root = objectMapper.readTree(response.body());
            return mapRepoDto(root);
        } catch (IOException e) {
            throw new GitHubApiException(502, "Failed to communicate with GitHub API: " + e.getMessage(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(500, "GitHub request was interrupted", e);
        }
    }

    public List<GitHubCommitDto> fetchCommits(String owner, String repo, int limit) {
        requireConfigured();
        int safeLimit = Math.min(Math.max(1, limit), 100);

        HttpRequest request = buildRequest("/repos/" + owner + "/" + repo + "/commits?per_page=" + safeLimit);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 409 || response.statusCode() == 404) {
                // Empty repository (no commits yet) or not found
                return Collections.emptyList();
            }
            checkResponseSuccess(response, "Failed to fetch commits for " + owner + "/" + repo);

            JsonNode array = objectMapper.readTree(response.body());
            if (!array.isArray()) {
                return Collections.emptyList();
            }

            List<GitHubCommitDto> commits = new ArrayList<>();
            for (JsonNode node : array) {
                String sha = node.path("sha").asText("");
                String htmlUrl = node.path("html_url").asText("");
                JsonNode commitNode = node.path("commit");
                String message = commitNode.path("message").asText("");
                JsonNode authorNode = commitNode.path("author");
                String authorName = authorNode.path("name").asText("Unknown");
                String authorEmail = authorNode.path("email").asText("");
                String date = authorNode.path("date").asText("");

                commits.add(new GitHubCommitDto(sha, message, authorName, authorEmail, date, htmlUrl));
            }
            return commits;
        } catch (IOException e) {
            log.warn("Failed to fetch commits for {}/{}: {}", owner, repo, e.getMessage());
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(500, "GitHub request was interrupted", e);
        }
    }

    public List<GitHubPullRequestDto> fetchPullRequests(String owner, String repo, int limit) {
        requireConfigured();
        int safeLimit = Math.min(Math.max(1, limit), 100);

        HttpRequest request = buildRequest("/repos/" + owner + "/" + repo + "/pulls?state=all&sort=updated&direction=desc&per_page=" + safeLimit);
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() == 404) {
                return Collections.emptyList();
            }
            checkResponseSuccess(response, "Failed to fetch pull requests for " + owner + "/" + repo);

            JsonNode array = objectMapper.readTree(response.body());
            if (!array.isArray()) {
                return Collections.emptyList();
            }

            List<GitHubPullRequestDto> prs = new ArrayList<>();
            for (JsonNode node : array) {
                int number = node.path("number").asInt();
                String title = node.path("title").asText("");
                String body = node.path("body").asText("");
                String state = node.path("state").asText("");
                String authorLogin = node.path("user").path("login").asText("unknown");
                String createdAt = node.path("created_at").asText("");
                String updatedAt = node.path("updated_at").asText("");
                String mergedAt = node.path("merged_at").asText("");
                String htmlUrl = node.path("html_url").asText("");

                String reviews = fetchReviewsSummary(owner, repo, number);

                prs.add(new GitHubPullRequestDto(
                        number, title, body, state, authorLogin,
                        createdAt, updatedAt, mergedAt, htmlUrl, reviews
                ));
            }
            return prs;
        } catch (IOException e) {
            log.warn("Failed to fetch pull requests for {}/{}: {}", owner, repo, e.getMessage());
            return Collections.emptyList();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new GitHubApiException(500, "GitHub request was interrupted", e);
        }
    }

    public String fetchReviewsSummary(String owner, String repo, int prNumber) {
        if (!properties.isConfigured()) return "None";

        HttpRequest request = buildRequest("/repos/" + owner + "/" + repo + "/pulls/" + prNumber + "/reviews?per_page=10");
        try {
            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                return "Not accessible";
            }
            JsonNode array = objectMapper.readTree(response.body());
            if (!array.isArray() || array.isEmpty()) {
                return "No reviews";
            }
            List<String> reviewList = new ArrayList<>();
            for (JsonNode r : array) {
                String user = r.path("user").path("login").asText("unknown");
                String state = r.path("state").asText("");
                if (!state.isBlank() && !"COMMENTED".equalsIgnoreCase(state)) {
                    reviewList.add(user + " (" + state + ")");
                }
            }
            return reviewList.isEmpty() ? "No formal review decisions" : String.join(", ", reviewList);
        } catch (Exception e) {
            return "Review retrieval skipped";
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw new GitHubApiException(401, "GitHub access token is not configured. Set GITHUB_TOKEN environment variable.");
        }
    }

    private HttpRequest buildRequest(String path) {
        return buildRequest(path, properties.getToken());
    }

    private HttpRequest buildRequest(String path, String token) {
        String baseUrl = properties.getApiUrl().replaceAll("/+$", "");
        URI uri = URI.create(baseUrl + (path.startsWith("/") ? path : "/" + path));

        return HttpRequest.newBuilder()
                .uri(uri)
                .timeout(Duration.ofSeconds(15))
                .header("Authorization", "Bearer " + token)
                .header("Accept", "application/vnd.github+json")
                .header("X-GitHub-Api-Version", "2022-11-28")
                .header("User-Agent", "Chittix-Company-Brain")
                .GET()
                .build();
    }

    private void checkResponseSuccess(HttpResponse<String> response, String contextMsg) {
        int status = response.statusCode();
        if (status >= 200 && status < 300) {
            return;
        }

        String errorDetail = parseErrorMessage(response.body(), "HTTP " + status);
        if (status == 401) {
            throw new GitHubApiException(401, "GitHub token is invalid or expired: " + errorDetail);
        } else if (status == 403) {
            throw new GitHubApiException(403, "GitHub rate limit exceeded or access forbidden: " + errorDetail);
        } else if (status == 404) {
            throw new GitHubApiException(404, "Requested GitHub resource not found: " + errorDetail);
        } else {
            throw new GitHubApiException(status, contextMsg + " (" + status + "): " + errorDetail);
        }
    }

    private String parseErrorMessage(String body, String fallback) {
        if (body == null || body.isBlank()) return fallback;
        try {
            JsonNode node = objectMapper.readTree(body);
            if (node.has("message")) {
                return node.path("message").asText();
            }
        } catch (Exception ignored) {}
        return fallback;
    }

    private List<String> extractScopes(HttpResponse<?> response) {
        return response.headers().firstValue("x-oauth-scopes")
                .map(s -> Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(str -> !str.isEmpty())
                        .toList())
                .orElse(Collections.emptyList());
    }

    private int extractHeaderInt(HttpResponse<?> response, String header, int fallback) {
        return response.headers().firstValue(header)
                .map(v -> {
                    try {
                        return Integer.parseInt(v.trim());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                }).orElse(fallback);
    }

    private long extractHeaderLong(HttpResponse<?> response, String header, long fallback) {
        return response.headers().firstValue(header)
                .map(v -> {
                    try {
                        return Long.parseLong(v.trim());
                    } catch (NumberFormatException e) {
                        return fallback;
                    }
                }).orElse(fallback);
    }

    private GitHubRepoDto mapRepoDto(JsonNode node) {
        return new GitHubRepoDto(
                node.path("id").asLong(),
                node.path("name").asText(""),
                node.path("full_name").asText(""),
                node.path("description").asText(""),
                node.path("html_url").asText(""),
                node.path("default_branch").asText("main"),
                node.path("private").asBoolean(false),
                node.path("stargazers_count").asInt(0),
                node.path("forks_count").asInt(0),
                node.path("open_issues_count").asInt(0),
                node.path("updated_at").asText("")
        );
    }
}
