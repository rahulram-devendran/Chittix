package com.companybrain.service;

import com.companybrain.config.GitHubProperties;
import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.github.GitHubCommitDto;
import com.companybrain.dto.github.GitHubJobStatusResponse;
import com.companybrain.dto.github.GitHubPullRequestDto;
import com.companybrain.dto.github.GitHubRepoDto;
import com.companybrain.exception.NotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Service managing asynchronous GitHub repository metadata ingestion jobs.
 * Ingests repository, commit, and pull-request metadata into FalkorDB and
 * vectorizes them using DocumentIngestionService.
 *
 * Jobs are tracked in an in-memory manager (non-durable across backend restarts).
 */
@Service
public class GitHubImportService {

    private static final Logger log = LoggerFactory.getLogger(GitHubImportService.class);

    private final GitHubApiClient gitHubApiClient;
    private final GitHubProperties properties;
    private final GraphClient graphClient;
    private final DocumentIngestionService documentIngestionService;

    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public GitHubImportService(GitHubApiClient gitHubApiClient,
                               GitHubProperties properties,
                               GraphClient graphClient,
                               DocumentIngestionService documentIngestionService) {
        this.gitHubApiClient = gitHubApiClient;
        this.properties = properties;
        this.graphClient = graphClient;
        this.documentIngestionService = documentIngestionService;
    }

    public String startImport(List<String> repositories) {
        if (repositories == null || repositories.isEmpty()) {
            throw new IllegalArgumentException("Repositories list must not be empty");
        }

        String jobId = "job-" + UUID.randomUUID().toString().substring(0, 8);
        JobState job = new JobState(jobId, repositories);
        jobs.put(jobId, job);

        executorService.submit(() -> runImport(job));

        log.info("Started GitHub import job {} for {} repositories", jobId, repositories.size());
        return jobId;
    }

    public GitHubJobStatusResponse getJobStatus(String jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("Import job '" + jobId + "' not found");
        }
        return job.toResponse();
    }

    public List<GitHubJobStatusResponse> listJobs() {
        return jobs.values().stream()
                .sorted(Comparator.comparingLong((JobState j) -> j.startTimeMillis).reversed())
                .limit(20)
                .map(JobState::toResponse)
                .toList();
    }

    private void runImport(JobState job) {
        job.status = "RUNNING";
        job.currentPhase = "Initializing";
        job.addLog("Import job started for " + job.repositoriesTotal + " repositories.");

        // Check FalkorDB availability before attempting graph ingestion
        if (!graphClient.ping()) {
            String errorMsg = "FalkorDB is offline or unreachable at the configured host. Ingestion aborted.";
            job.status = "FAILED";
            job.currentPhase = "Failed";
            job.endTimeMillis = System.currentTimeMillis();
            job.addError(errorMsg);
            job.addLog("ERROR: " + errorMsg);
            log.error("Job {}: {}", job.jobId, errorMsg);
            return;
        }

        try {
            for (String repoFullName : job.requestedRepositories) {
                job.currentRepository = repoFullName;
                job.currentPhase = "Importing " + repoFullName;
                job.addLog("Processing repository: " + repoFullName);

                String[] parts = repoFullName.split("/");
                if (parts.length != 2) {
                    String err = "Invalid repository name format '" + repoFullName + "'. Expected 'owner/repo'.";
                    job.addError(err);
                    job.addLog("Skipping: " + err);
                    continue;
                }
                String owner = parts[0].trim();
                String repo = parts[1].trim();

                // 1. Fetch Repository Metadata
                GitHubRepoDto repoDto;
                try {
                    repoDto = gitHubApiClient.fetchRepository(owner, repo);
                } catch (Exception e) {
                    String err = "Failed to fetch repository metadata for " + repoFullName + ": " + e.getMessage();
                    job.addError(err);
                    job.addLog("ERROR: " + err);
                    continue;
                }

                // 2. Persist :Repository Node
                String repoSourceId = "github:repo:" + repoDto.fullName();
                String upsertRepoCypher = """
                        MERGE (r:Repository {source_id: $sourceId})
                        ON CREATE SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt
                        ON MATCH SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt
                        """;

                graphClient.executeWrite(upsertRepoCypher, Map.of(
                        "sourceId", repoSourceId,
                        "name", repoDto.name(),
                        "fullName", repoDto.fullName(),
                        "description", repoDto.description() == null ? "" : repoDto.description(),
                        "htmlUrl", repoDto.htmlUrl(),
                        "defaultBranch", repoDto.defaultBranch(),
                        "updatedAt", repoDto.updatedAt()
                ));

                // Register and ingest Repository Overview as a Document
                documentIngestionService.registerDocument(repoSourceId, "Repository: " + repoDto.fullName());
                String repoOverview = "GitHub Repository: " + repoDto.fullName() + "\n"
                        + "Description: " + (repoDto.description() == null || repoDto.description().isBlank() ? "No description provided." : repoDto.description()) + "\n"
                        + "Default Branch: " + repoDto.defaultBranch() + "\n"
                        + "Stars: " + repoDto.stargazersCount() + ", Forks: " + repoDto.forksCount() + "\n"
                        + "URL: " + repoDto.htmlUrl() + "\n"
                        + "Last Updated: " + repoDto.updatedAt();

                DocumentIngestionResult repoDocRes = documentIngestionService.ingestDocument(repoSourceId, repoOverview);
                job.chunksCreated.addAndGet(repoDocRes.chunkCount());
                job.addLog("Persisted repository node and vector embeddings for " + repoDto.fullName());

                // 3. Fetch and Persist Commits
                job.currentPhase = "Importing Commits for " + repoFullName;
                int commitLimit = properties.getCommitLimit();
                List<GitHubCommitDto> commits = gitHubApiClient.fetchCommits(owner, repo, commitLimit);
                job.addLog("Retrieved " + commits.size() + " commit(s) (limit " + commitLimit + ").");

                String linkCommitCypher = """
                        MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                        MERGE (r)-[:HAS_COMMIT]->(d)
                        """;

                for (GitHubCommitDto c : commits) {
                    String shortSha = c.sha().length() > 7 ? c.sha().substring(0, 7) : c.sha();
                    String commitSourceId = "github:commit:" + repoDto.fullName() + "@" + shortSha;
                    String firstLine = c.message().lines().findFirst().orElse("Commit " + shortSha);
                    String commitTitle = "Commit " + shortSha + ": " + (firstLine.length() > 60 ? firstLine.substring(0, 57) + "..." : firstLine);

                    documentIngestionService.registerDocument(commitSourceId, commitTitle);
                    graphClient.executeWrite(linkCommitCypher, Map.of(
                            "repoSourceId", repoSourceId,
                            "docSourceId", commitSourceId
                    ));

                    String commitContent = "Commit in " + repoDto.fullName() + " (" + c.sha() + ")\n"
                            + "Author: " + c.authorName() + (c.authorEmail().isBlank() ? "" : " <" + c.authorEmail() + ">") + "\n"
                            + "Date: " + c.date() + "\n"
                            + "URL: " + c.htmlUrl() + "\n"
                            + "Message:\n" + c.message();

                    DocumentIngestionResult commitRes = documentIngestionService.ingestDocument(commitSourceId, commitContent);
                    job.chunksCreated.addAndGet(commitRes.chunkCount());
                    job.commitsImported.incrementAndGet();
                }

                // 4. Fetch and Persist Pull Requests
                job.currentPhase = "Importing Pull Requests for " + repoFullName;
                int prLimit = properties.getPrLimit();
                List<GitHubPullRequestDto> prs = gitHubApiClient.fetchPullRequests(owner, repo, prLimit);
                job.addLog("Retrieved " + prs.size() + " pull request(s) (limit " + prLimit + ").");

                String linkPrCypher = """
                        MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                        MERGE (r)-[:HAS_PR]->(d)
                        """;

                for (GitHubPullRequestDto pr : prs) {
                    String prSourceId = "github:pr:" + repoDto.fullName() + "#" + pr.number();
                    String prTitle = "PR #" + pr.number() + ": " + (pr.title().length() > 60 ? pr.title().substring(0, 57) + "..." : pr.title());

                    documentIngestionService.registerDocument(prSourceId, prTitle);
                    graphClient.executeWrite(linkPrCypher, Map.of(
                            "repoSourceId", repoSourceId,
                            "docSourceId", prSourceId
                    ));

                    String prContent = "Pull Request #" + pr.number() + " in " + repoDto.fullName() + "\n"
                            + "Title: " + pr.title() + "\n"
                            + "State: " + pr.state() + "\n"
                            + "Author: " + pr.authorLogin() + "\n"
                            + "Created: " + pr.createdAt() + (pr.mergedAt() != null && !pr.mergedAt().isBlank() ? "\nMerged: " + pr.mergedAt() : "") + "\n"
                            + "URL: " + pr.htmlUrl() + "\n"
                            + "Review Summary: " + pr.reviewSummary() + "\n"
                            + "Description:\n" + (pr.body() == null || pr.body().isBlank() ? "No description provided." : pr.body());

                    DocumentIngestionResult prRes = documentIngestionService.ingestDocument(prSourceId, prContent);
                    job.chunksCreated.addAndGet(prRes.chunkCount());
                    job.pullRequestsImported.incrementAndGet();
                }

                job.importedRepositories.add(repoDto.fullName());
                job.repositoriesProcessed.incrementAndGet();
                job.addLog("Completed import for " + repoDto.fullName());
            }

            job.status = job.errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_WARNINGS";
            job.currentPhase = "Finished";
            job.endTimeMillis = System.currentTimeMillis();
            job.addLog("Job finished with " + job.repositoriesProcessed.get() + "/" + job.repositoriesTotal
                    + " repositories processed, " + job.commitsImported.get() + " commits, "
                    + job.pullRequestsImported.get() + " pull requests, and "
                    + job.chunksCreated.get() + " vectorized document chunks.");

        } catch (Exception e) {
            log.error("Fatal error during import job {}: {}", job.jobId, e.getMessage(), e);
            job.status = "FAILED";
            job.currentPhase = "Failed";
            job.endTimeMillis = System.currentTimeMillis();
            job.addError("Fatal execution failure: " + e.getMessage());
            job.addLog("ERROR: Fatal execution failure: " + e.getMessage());
        }
    }

    private static class JobState {
        final String jobId;
        final List<String> requestedRepositories;
        final int repositoriesTotal;
        final long startTimeMillis;
        Long endTimeMillis = null;

        volatile String status = "QUEUED";
        volatile String currentRepository = "";
        volatile String currentPhase = "Queued";

        final AtomicInteger repositoriesProcessed = new AtomicInteger(0);
        final AtomicInteger commitsImported = new AtomicInteger(0);
        final AtomicInteger pullRequestsImported = new AtomicInteger(0);
        final AtomicInteger chunksCreated = new AtomicInteger(0);

        final List<String> logs = new CopyOnWriteArrayList<>();
        final List<String> errors = new CopyOnWriteArrayList<>();
        final List<String> importedRepositories = new CopyOnWriteArrayList<>();

        JobState(String jobId, List<String> repositories) {
            this.jobId = jobId;
            this.requestedRepositories = List.copyOf(repositories);
            this.repositoriesTotal = repositories.size();
            this.startTimeMillis = System.currentTimeMillis();
        }

        void addLog(String message) {
            String timestamp = DateTimeFormatter.ISO_INSTANT.format(Instant.now());
            logs.add("[" + timestamp + "] " + message);
        }

        void addError(String error) {
            errors.add(error);
        }

        GitHubJobStatusResponse toResponse() {
            return new GitHubJobStatusResponse(
                    jobId,
                    status,
                    repositoriesTotal,
                    repositoriesProcessed.get(),
                    commitsImported.get(),
                    pullRequestsImported.get(),
                    chunksCreated.get(),
                    currentRepository,
                    currentPhase,
                    startTimeMillis,
                    endTimeMillis,
                    List.copyOf(logs),
                    List.copyOf(errors),
                    List.copyOf(importedRepositories)
            );
        }
    }
}
