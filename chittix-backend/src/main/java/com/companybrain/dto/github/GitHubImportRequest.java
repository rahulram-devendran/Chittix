package com.companybrain.dto.github;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;

public record GitHubImportRequest(
        @NotEmpty(message = "repositories list must not be empty")
        List<String> repositories
) {}
