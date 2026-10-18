package com.companybrain.service;

import com.companybrain.dto.source.SourceConfigRequest;
import com.companybrain.dto.source.SourceItemDto;
import com.companybrain.dto.source.SourceStatusResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service managing in-memory credential storage and genuine connection verification
 * for external source providers (GitLab, Bitbucket, Confluence).
 * Never exposes credentials in logs, API responses, or disk.
 */
@Service
public class SourceIntegrationService {

    private static final Logger log = LoggerFactory.getLogger(SourceIntegrationService.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    // In-memory store for active validated credentials: provider -> stored config
    private final Map<String, StoredCredential> credentials = new ConcurrentHashMap<>();
    private final Map<String, SourceStatusResponse> activeStatus = new ConcurrentHashMap<>();

    public record StoredCredential(String token, String username, String baseUrl) {}

    @Autowired
    public SourceIntegrationService(ObjectMapper objectMapper) {
        this(objectMapper, HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build());
    }

    public SourceIntegrationService(ObjectMapper objectMapper, HttpClient httpClient) {
        this.objectMapper = objectMapper;
        this.httpClient = httpClient;
    }

    public boolean isConfigured(String provider) {
        return credentials.containsKey(provider.toLowerCase());
    }

    public StoredCredential getCredential(String provider) {
        return credentials.get(provider.toLowerCase());
    }

    public Map<String, SourceStatusResponse> getAllSourceStatuses() {
        Map<String, SourceStatusResponse> map = new LinkedHashMap<>();
        for (String p : List.of("gitlab", "bitbucket", "confluence")) {
            map.put(p, activeStatus.getOrDefault(p, SourceStatusResponse.unconfigured(
                    p, getDefaultBaseUrl(p), "No credentials configured for " + capitalize(p) + "."
            )));
        }
        return map;
    }

    public SourceStatusResponse getStatus(String provider) {
        String key = provider.toLowerCase();
        return activeStatus.getOrDefault(key, SourceStatusResponse.unconfigured(
                key, getDefaultBaseUrl(key), "No credentials configured for " + capitalize(key) + "."
        ));
    }

    /**
     * Genuinely validates candidate credentials against the target service.
     * On failure: throws ResponseStatusException(BAD_REQUEST) and NEVER replaces previous working credentials.
     * On success: stores validated credentials in memory and updates status.
     */
    public synchronized SourceStatusResponse configureSource(SourceConfigRequest request) {
        if (request == null || request.provider() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Source provider is required.");
        }
        String provider = request.provider().toLowerCase().trim();
        String token = request.token() != null ? request.token().trim() : "";
        if (token.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Token or credentials must not be blank.");
        }

        String baseUrl = request.baseUrl() != null && !request.baseUrl().isBlank()
                ? request.baseUrl().trim().replaceAll("/+$", "")
                : getDefaultBaseUrl(provider);

        String username = request.username() != null ? request.username().trim() : "";

        // Genuine upstream verification
        SourceStatusResponse testResult = testConnection(provider, token, username, baseUrl);
        if (!testResult.connected()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Connection validation failed for " + capitalize(provider) + ": " + testResult.message());
        }

        // Only on verified success: update in-memory active credentials
        credentials.put(provider, new StoredCredential(token, username, baseUrl));
        activeStatus.put(provider, testResult);
        log.info("Source provider '{}' configured and verified successfully in memory.", provider);

        return testResult;
    }

    public SourceStatusResponse testConnection(String provider, String token, String username, String baseUrl) {
        return switch (provider.toLowerCase()) {
            case "gitlab" -> testGitLab(token, baseUrl);
            case "bitbucket" -> testBitbucket(token, username, baseUrl);
            case "confluence" -> testConfluence(token, username, baseUrl);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported source provider: " + provider);
        };
    }

    // =========================================================================
    // GitLab Connection & Discovery
    // =========================================================================

    private String cleanGitLabBaseUrl(String baseUrl) {
        String clean = (baseUrl == null || baseUrl.isBlank()) ? "https://gitlab.com" : baseUrl.trim().replaceAll("/+$", "");
        if (clean.endsWith("/api/v4")) {
            clean = clean.substring(0, clean.length() - "/api/v4".length()).replaceAll("/+$", "");
        }
        return clean;
    }

    private SourceStatusResponse testGitLab(String token, String baseUrl) {
        String base = cleanGitLabBaseUrl(baseUrl);
        try {
            // Attempt 1: GET /api/v4/user with PRIVATE-TOKEN
            HttpRequest reqUserPrivate = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/v4/user"))
                    .timeout(Duration.ofSeconds(12))
                    .header("PRIVATE-TOKEN", token)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET().build();

            HttpResponse<String> resp = httpClient.send(reqUserPrivate, HttpResponse.BodyHandlers.ofString());

            // If 401 or 404, attempt with Authorization: Bearer
            if (resp.statusCode() == 401 || resp.statusCode() == 404) {
                HttpRequest reqUserBearer = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/v4/user"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Chittix-Company-Brain")
                        .GET().build();
                HttpResponse<String> respBearer = httpClient.send(reqUserBearer, HttpResponse.BodyHandlers.ofString());
                if (respBearer.statusCode() == 200) {
                    resp = respBearer;
                }
            }

            if (resp.statusCode() == 200) {
                JsonNode root = objectMapper.readTree(resp.body());
                String user = root.path("username").asText("user");
                String name = root.path("name").asText(user);
                return new SourceStatusResponse(
                        "gitlab", true, true, user, name, base,
                        "Successfully verified GitLab connection for @" + user,
                        true,
                        "Metadata ingestion is active for GitLab."
                );
            }

            // Attempt 2: Fallback for Project Access Tokens or Group Tokens where /user is 404
            HttpRequest reqProjects = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/api/v4/projects?membership=true&per_page=1&simple=true"))
                    .timeout(Duration.ofSeconds(12))
                    .header("PRIVATE-TOKEN", token)
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET().build();

            HttpResponse<String> projResp = httpClient.send(reqProjects, HttpResponse.BodyHandlers.ofString());
            if (projResp.statusCode() == 401) {
                HttpRequest reqProjBearer = HttpRequest.newBuilder()
                        .uri(URI.create(base + "/api/v4/projects?membership=true&per_page=1&simple=true"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Authorization", "Bearer " + token)
                        .header("Accept", "application/json")
                        .header("User-Agent", "Chittix-Company-Brain")
                        .GET().build();
                HttpResponse<String> projBearerResp = httpClient.send(reqProjBearer, HttpResponse.BodyHandlers.ofString());
                if (projBearerResp.statusCode() == 200) {
                    projResp = projBearerResp;
                }
            }

            if (projResp.statusCode() == 200) {
                return new SourceStatusResponse(
                        "gitlab", true, true, "gitlab-token", "GitLab Project/Access Token", base,
                        "Successfully verified GitLab connection via Project/Group Access Token",
                        true,
                        "Metadata ingestion is active for GitLab."
                );
            }

            if (resp.statusCode() == 401 || projResp.statusCode() == 401) {
                return SourceStatusResponse.unconfigured("gitlab", base, "GitLab authentication failed (HTTP 401 Unauthorized): Invalid Personal/Project Access Token.");
            } else if (resp.statusCode() == 403 || projResp.statusCode() == 403) {
                return SourceStatusResponse.unconfigured("gitlab", base, "GitLab access forbidden (HTTP 403): Token lacks 'read_api' or 'api' scope.");
            } else {
                return SourceStatusResponse.unconfigured("gitlab", base, "GitLab API responded with HTTP " + resp.statusCode());
            }
        } catch (IOException e) {
            log.warn("GitLab connection test network error: {}", e.getMessage());
            return SourceStatusResponse.unconfigured("gitlab", base, "Unable to reach GitLab at " + base + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SourceStatusResponse.unconfigured("gitlab", base, "GitLab connection check interrupted");
        }
    }

    // =========================================================================
    // Bitbucket Connection & Discovery
    // =========================================================================

    private String cleanBitbucketBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://api.bitbucket.org/2.0";
        }
        String clean = baseUrl.trim().replaceAll("/+$", "");
        if (clean.equalsIgnoreCase("https://bitbucket.org") || clean.equalsIgnoreCase("http://bitbucket.org")) {
            return "https://api.bitbucket.org/2.0";
        }
        if (clean.equalsIgnoreCase("https://api.bitbucket.org") || clean.equalsIgnoreCase("http://api.bitbucket.org")) {
            return "https://api.bitbucket.org/2.0";
        }
        if (clean.contains("bitbucket.org") && !clean.contains("/2.0")) {
            clean = clean.replace("https://bitbucket.org", "https://api.bitbucket.org")
                         .replace("http://bitbucket.org", "https://api.bitbucket.org");
            if (!clean.endsWith("/2.0")) {
                clean = clean + "/2.0";
            }
        }
        return clean;
    }

    private SourceStatusResponse testBitbucket(String token, String username, String baseUrl) {
        String base = cleanBitbucketBaseUrl(baseUrl);
        String url = base + "/user";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (username != null && !username.isBlank()) {
                String auth = Base64.getEncoder().encodeToString((username.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + auth);
            } else {
                builder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String user = root.path("username").asText(root.path("nickname").asText("user"));
                String name = root.path("display_name").asText(user);
                return new SourceStatusResponse(
                        "bitbucket", true, true, user, name, base,
                        "Successfully verified Bitbucket connection for @" + user,
                        true,
                        "Metadata ingestion is active for Bitbucket."
                );
            }

            // Fallback for workspace access tokens or when /user is not permitted
            HttpRequest.Builder repoBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/repositories?role=member&pagelen=1"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (username != null && !username.isBlank()) {
                String auth = Base64.getEncoder().encodeToString((username.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));
                repoBuilder.header("Authorization", "Basic " + auth);
            } else {
                repoBuilder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> repoResp = httpClient.send(repoBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (repoResp.statusCode() == 200) {
                String user = (username != null && !username.isBlank()) ? username.trim() : "bitbucket-token";
                return new SourceStatusResponse(
                        "bitbucket", true, true, user, "Bitbucket Account / Workspace Token", base,
                        "Successfully verified Bitbucket connection via Workspace Access Token",
                        true,
                        "Metadata ingestion is active for Bitbucket."
                );
            }

            if (code == 401 || repoResp.statusCode() == 401) {
                return SourceStatusResponse.unconfigured("bitbucket", base, "Bitbucket authentication failed (HTTP 401 Unauthorized): Invalid app password or token.");
            } else if (code == 403 || repoResp.statusCode() == 403) {
                return SourceStatusResponse.unconfigured("bitbucket", base, "Bitbucket access forbidden (HTTP 403): Insufficient account permissions. Please ensure App Password has 'Repositories: Read' scope.");
            } else {
                return SourceStatusResponse.unconfigured("bitbucket", base, "Bitbucket API responded with HTTP " + code);
            }
        } catch (IOException e) {
            log.warn("Bitbucket connection test network error: {}", e.getMessage());
            return SourceStatusResponse.unconfigured("bitbucket", base, "Unable to reach Bitbucket at " + url + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SourceStatusResponse.unconfigured("bitbucket", base, "Bitbucket connection check interrupted");
        }
    }

    // =========================================================================
    // Confluence Connection & Discovery
    // =========================================================================

    private String cleanConfluenceBaseUrl(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank()) {
            return "https://your-domain.atlassian.net/wiki";
        }
        String clean = baseUrl.trim().replaceAll("/+$", "");
        if (clean.endsWith("/rest/api")) {
            clean = clean.substring(0, clean.length() - "/rest/api".length()).replaceAll("/+$", "");
        } else if (clean.endsWith("/rest")) {
            clean = clean.substring(0, clean.length() - "/rest".length()).replaceAll("/+$", "");
        }
        if (clean.contains("atlassian.net") && !clean.contains("/wiki")) {
            clean = clean + "/wiki";
        }
        return clean;
    }

    private SourceStatusResponse testConfluence(String token, String username, String baseUrl) {
        String base = cleanConfluenceBaseUrl(baseUrl);
        String url = base + "/rest/api/user/current";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (username != null && !username.isBlank()) {
                String auth = Base64.getEncoder().encodeToString((username.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + auth);
            } else {
                builder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            int code = response.statusCode();
            if (code == 200) {
                JsonNode root = objectMapper.readTree(response.body());
                String user = root.path("publicName").asText(root.path("displayName").asText(username != null ? username : "user"));
                return new SourceStatusResponse(
                        "confluence", true, true, user, user, base,
                        "Successfully verified Confluence connection for " + user,
                        true,
                        "Document space indexing is active for Confluence."
                );
            }

            // Fallback for space tokens or Atlassian Cloud tokens where /user/current is 404
            HttpRequest.Builder spaceBuilder = HttpRequest.newBuilder()
                    .uri(URI.create(base + "/rest/api/space?limit=1"))
                    .timeout(Duration.ofSeconds(12))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (username != null && !username.isBlank()) {
                String auth = Base64.getEncoder().encodeToString((username.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));
                spaceBuilder.header("Authorization", "Basic " + auth);
            } else {
                spaceBuilder.header("Authorization", "Bearer " + token.trim());
            }

            HttpResponse<String> spaceResp = httpClient.send(spaceBuilder.build(), HttpResponse.BodyHandlers.ofString());
            if (spaceResp.statusCode() == 200) {
                String user = (username != null && !username.isBlank()) ? username.trim() : "confluence-user";
                return new SourceStatusResponse(
                        "confluence", true, true, user, "Confluence Account / Space Access", base,
                        "Successfully verified Confluence connection via Space Permissions",
                        true,
                        "Document space indexing is active for Confluence."
                );
            }

            // Secondary fallback: if base has /wiki and returned 404, try without /wiki (or vice versa for custom on-prem)
            if (spaceResp.statusCode() == 404 && base.contains("/wiki")) {
                String altBase = base.replace("/wiki", "");
                HttpRequest.Builder altSpaceBuilder = HttpRequest.newBuilder()
                        .uri(URI.create(altBase + "/rest/api/space?limit=1"))
                        .timeout(Duration.ofSeconds(12))
                        .header("Accept", "application/json")
                        .header("User-Agent", "Chittix-Company-Brain")
                        .GET();
                if (username != null && !username.isBlank()) {
                    String auth = Base64.getEncoder().encodeToString((username.trim() + ":" + token.trim()).getBytes(StandardCharsets.UTF_8));
                    altSpaceBuilder.header("Authorization", "Basic " + auth);
                } else {
                    altSpaceBuilder.header("Authorization", "Bearer " + token.trim());
                }
                HttpResponse<String> altResp = httpClient.send(altSpaceBuilder.build(), HttpResponse.BodyHandlers.ofString());
                if (altResp.statusCode() == 200) {
                    String user = (username != null && !username.isBlank()) ? username.trim() : "confluence-user";
                    return new SourceStatusResponse(
                            "confluence", true, true, user, "Confluence Account / Space Access", altBase,
                            "Successfully verified Confluence connection via Space Permissions",
                            true,
                            "Document space indexing is active for Confluence."
                    );
                }
            }

            if (code == 401 || spaceResp.statusCode() == 401) {
                return SourceStatusResponse.unconfigured("confluence", base, "Confluence authentication failed (HTTP 401): Invalid API token or credentials. Note: Atlassian Cloud requires Email + API Token.");
            } else if (code == 403 || spaceResp.statusCode() == 403) {
                return SourceStatusResponse.unconfigured("confluence", base, "Confluence access forbidden (HTTP 403): User lacks read permissions.");
            } else {
                return SourceStatusResponse.unconfigured("confluence", base, "Confluence API responded with HTTP " + (spaceResp.statusCode() != 200 ? spaceResp.statusCode() : code));
            }
        } catch (IOException e) {
            log.warn("Confluence connection test network error: {}", e.getMessage());
            return SourceStatusResponse.unconfigured("confluence", base, "Unable to reach Confluence at " + url + ": " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return SourceStatusResponse.unconfigured("confluence", base, "Confluence connection check interrupted");
        }
    }

    // =========================================================================
    // Content Discovery (List projects, repos, spaces)
    // =========================================================================

    public List<SourceItemDto> listItems(String provider) {
        String p = provider.toLowerCase();
        StoredCredential cred = credentials.get(p);
        if (cred == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Provider '" + provider + "' is not configured.");
        }

        return switch (p) {
            case "gitlab" -> listGitLabProjects(cred);
            case "bitbucket" -> listBitbucketRepositories(cred);
            case "confluence" -> listConfluenceSpaces(cred);
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported provider: " + provider);
        };
    }

    private List<SourceItemDto> listGitLabProjects(StoredCredential cred) {
        String base = cleanGitLabBaseUrl(cred.baseUrl());
        String url = base + "/api/v4/projects?membership=true&simple=true&per_page=50&order_by=updated_at";
        try {
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("PRIVATE-TOKEN", cred.token())
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET().build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

            // If 401/404, try with Authorization: Bearer
            if (response.statusCode() == 401 || response.statusCode() == 404) {
                HttpRequest bearerReq = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofSeconds(15))
                        .header("Authorization", "Bearer " + cred.token())
                        .header("Accept", "application/json")
                        .header("User-Agent", "Chittix-Company-Brain")
                        .GET().build();
                response = httpClient.send(bearerReq, HttpResponse.BodyHandlers.ofString());
            }

            if (response.statusCode() != 200) {
                log.warn("GitLab list projects failed with HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode array = objectMapper.readTree(response.body());
            List<SourceItemDto> items = new ArrayList<>();
            if (array.isArray()) {
                for (JsonNode n : array) {
                    String id = String.valueOf(n.path("id").asLong());
                    String name = n.path("name").asText();
                    String fullName = n.path("path_with_namespace").asText(name);
                    String description = n.path("description").asText("");
                    String webUrl = n.path("web_url").asText("");
                    String defaultBranch = n.path("default_branch").asText("main");
                    String visibility = n.path("visibility").asText("private");
                    boolean isPrivate = !"public".equalsIgnoreCase(visibility);

                    items.add(new SourceItemDto(
                            id, name, fullName, description, webUrl, defaultBranch,
                            "project", isPrivate,
                            Map.of("stars", String.valueOf(n.path("star_count").asInt(0)),
                                   "forks", String.valueOf(n.path("forks_count").asInt(0)),
                                   "visibility", visibility)
                    ));
                }
            }
            return items;
        } catch (Exception e) {
            log.error("Failed to list GitLab projects: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list GitLab projects: " + e.getMessage());
        }
    }

    private List<SourceItemDto> listBitbucketRepositories(StoredCredential cred) {
        String base = cleanBitbucketBaseUrl(cred.baseUrl());
        String url = base + "/repositories?role=member&pagelen=50";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (cred.username() != null && !cred.username().isBlank()) {
                String auth = Base64.getEncoder().encodeToString((cred.username().trim() + ":" + cred.token().trim()).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + auth);
            } else {
                builder.header("Authorization", "Bearer " + cred.token().trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Bitbucket list repositories failed with HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode values = root.path("values");
            List<SourceItemDto> items = new ArrayList<>();
            if (values.isArray()) {
                for (JsonNode n : values) {
                    String fullName = n.path("full_name").asText();
                    String name = n.path("name").asText(fullName);
                    String description = n.path("description").asText("");
                    String webUrl = n.path("links").path("html").path("href").asText("");
                    String defaultBranch = n.path("mainbranch").path("name").asText("main");
                    boolean isPrivate = n.path("is_private").asBoolean(true);

                    items.add(new SourceItemDto(
                            fullName, name, fullName, description, webUrl, defaultBranch,
                            "repository", isPrivate,
                            Map.of("language", n.path("language").asText("unknown"),
                                   "updated_on", n.path("updated_on").asText(""))
                    ));
                }
            }
            return items;
        } catch (Exception e) {
            log.error("Failed to list Bitbucket repositories: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list Bitbucket repositories: " + e.getMessage());
        }
    }

    private List<SourceItemDto> listConfluenceSpaces(StoredCredential cred) {
        String base = cleanConfluenceBaseUrl(cred.baseUrl());
        String url = base + "/rest/api/space?limit=50";
        try {
            HttpRequest.Builder builder = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(15))
                    .header("Accept", "application/json")
                    .header("User-Agent", "Chittix-Company-Brain")
                    .GET();

            if (cred.username() != null && !cred.username().isBlank()) {
                String auth = Base64.getEncoder().encodeToString((cred.username().trim() + ":" + cred.token().trim()).getBytes(StandardCharsets.UTF_8));
                builder.header("Authorization", "Basic " + auth);
            } else {
                builder.header("Authorization", "Bearer " + cred.token().trim());
            }

            HttpResponse<String> response = httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) {
                log.warn("Confluence list spaces failed with HTTP {}: {}", response.statusCode(), response.body());
                return Collections.emptyList();
            }

            JsonNode root = objectMapper.readTree(response.body());
            JsonNode results = root.path("results");
            List<SourceItemDto> items = new ArrayList<>();
            if (results.isArray()) {
                for (JsonNode n : results) {
                    String id = String.valueOf(n.path("id").asLong());
                    String key = n.path("key").asText();
                    String name = n.path("name").asText(key);
                    String desc = n.path("description").path("plain").path("value").asText("");
                    String webUrl = base + n.path("_links").path("webui").asText("/spaces/" + key);

                    items.add(new SourceItemDto(
                            key, name, key, desc, webUrl, "",
                            "space", false,
                            Map.of("spaceId", id, "type", n.path("type").asText("global"))
                    ));
                }
            }
            return items;
        } catch (Exception e) {
            log.error("Failed to list Confluence spaces: {}", e.getMessage());
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "Failed to list Confluence spaces: " + e.getMessage());
        }
    }

    // =========================================================================
    // Item Metadata, Commits, PRs/MRs, and Pages Retrieval
    // =========================================================================

    public HttpResponse<String> executeAuthenticatedGet(String provider, String url) throws IOException, InterruptedException {
        StoredCredential cred = credentials.get(provider.toLowerCase());
        if (cred == null) {
            throw new IllegalStateException("Credentials not configured for provider: " + provider);
        }

        HttpRequest.Builder builder = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .timeout(Duration.ofSeconds(15))
                .header("Accept", "application/json")
                .header("User-Agent", "Chittix-Company-Brain")
                .GET();

        if ("gitlab".equalsIgnoreCase(provider)) {
            builder.header("PRIVATE-TOKEN", cred.token().trim());
        } else if (cred.username() != null && !cred.username().isBlank()) {
            String auth = Base64.getEncoder().encodeToString((cred.username().trim() + ":" + cred.token().trim()).getBytes(StandardCharsets.UTF_8));
            builder.header("Authorization", "Basic " + auth);
        } else {
            builder.header("Authorization", "Bearer " + cred.token().trim());
        }

        return httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
    }

    public static String getDefaultBaseUrl(String provider) {
        return switch (provider.toLowerCase()) {
            case "gitlab" -> "https://gitlab.com";
            case "bitbucket" -> "https://api.bitbucket.org/2.0";
            case "confluence" -> "https://your-domain.atlassian.net";
            default -> "";
        };
    }

    private static String capitalize(String text) {
        if (text == null || text.isEmpty()) return text;
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
