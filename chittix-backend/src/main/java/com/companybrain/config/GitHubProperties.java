package com.companybrain.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Configuration properties for GitHub API integration.
 * The access token is securely externalized via the GITHUB_TOKEN environment variable.
 */
@Component
@ConfigurationProperties(prefix = "chittix.github")
public class GitHubProperties {

    /**
     * GitHub Personal Access Token (PAT) externalized via GITHUB_TOKEN environment variable.
     * Never logged, exposed to frontend, or persisted in git.
     */
    private String token = "";

    /**
     * GitHub API base URL. Defaults to 'https://api.github.com'.
     */
    private String apiUrl = "https://api.github.com";

    /**
     * Maximum commits to import per repository in V1. Defaults to 20.
     */
    private int commitLimit = 20;

    /**
     * Maximum pull requests to import per repository in V1. Defaults to 20.
     */
    private int prLimit = 20;

    public boolean isConfigured() {
        return token != null && !token.trim().isEmpty();
    }

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token != null ? token.trim() : "";
    }

    public String getApiUrl() {
        return apiUrl;
    }

    public void setApiUrl(String apiUrl) {
        this.apiUrl = apiUrl != null && !apiUrl.trim().isEmpty() ? apiUrl.trim() : "https://api.github.com";
    }

    public int getCommitLimit() {
        return commitLimit;
    }

    public void setCommitLimit(int commitLimit) {
        this.commitLimit = Math.max(1, commitLimit);
    }

    public int getPrLimit() {
        return prLimit;
    }

    public void setPrLimit(int prLimit) {
        this.prLimit = Math.max(1, prLimit);
    }

    @Override
    public String toString() {
        return "GitHubProperties{configured=" + isConfigured()
                + ", apiUrl='" + apiUrl + '\''
                + ", commitLimit=" + commitLimit
                + ", prLimit=" + prLimit + '}';
    }
}
