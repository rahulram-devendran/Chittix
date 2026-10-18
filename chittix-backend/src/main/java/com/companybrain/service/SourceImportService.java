package com.companybrain.service;

import com.companybrain.dto.DocumentIngestionResult;
import com.companybrain.dto.source.SourceJobStatusResponse;
import com.companybrain.exception.NotFoundException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.net.URLEncoder;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
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
 * Service managing asynchronous metadata import jobs for external source providers
 * (GitLab, Bitbucket, Confluence) into FalkorDB and vector index.
 */
@Service
public class SourceImportService {

    private static final Logger log = LoggerFactory.getLogger(SourceImportService.class);

    private final SourceIntegrationService sourceIntegrationService;
    private final GraphClient graphClient;
    private final DocumentIngestionService documentIngestionService;
    private final ObjectMapper objectMapper;

    private final ConcurrentHashMap<String, JobState> jobs = new ConcurrentHashMap<>();
    private final ExecutorService executorService = Executors.newVirtualThreadPerTaskExecutor();

    public SourceImportService(SourceIntegrationService sourceIntegrationService,
                               GraphClient graphClient,
                               DocumentIngestionService documentIngestionService,
                               ObjectMapper objectMapper) {
        this.sourceIntegrationService = sourceIntegrationService;
        this.graphClient = graphClient;
        this.documentIngestionService = documentIngestionService;
        this.objectMapper = objectMapper;
    }

    public String startImport(String provider, List<String> items) {
        if (provider == null || provider.isBlank()) {
            throw new IllegalArgumentException("Provider must not be blank");
        }
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("Items list must not be empty");
        }
        String p = provider.toLowerCase().trim();
        if (!sourceIntegrationService.isConfigured(p)) {
            throw new IllegalStateException("Provider '" + p + "' is not configured. Configure credentials first.");
        }

        String jobId = "src-job-" + UUID.randomUUID().toString().substring(0, 8);
        JobState job = new JobState(jobId, p, items);
        jobs.put(jobId, job);

        executorService.submit(() -> runImport(job));
        log.info("Started {} import job {} for {} items", p, jobId, items.size());
        return jobId;
    }

    public SourceJobStatusResponse getJobStatus(String jobId) {
        JobState job = jobs.get(jobId);
        if (job == null) {
            throw new NotFoundException("Import job '" + jobId + "' not found");
        }
        return job.toResponse();
    }

    public List<SourceJobStatusResponse> listJobs(String provider) {
        String p = provider != null ? provider.toLowerCase().trim() : "";
        return jobs.values().stream()
                .filter(j -> p.isEmpty() || j.provider.equalsIgnoreCase(p))
                .sorted(Comparator.comparingLong((JobState j) -> j.startTimeMillis).reversed())
                .limit(20)
                .map(JobState::toResponse)
                .toList();
    }

    private void runImport(JobState job) {
        job.status = "RUNNING";
        job.currentPhase = "Initializing";
        job.addLog("Import job started for " + job.itemsTotal + " " + job.provider + " items.");

        if (!graphClient.ping()) {
            String errorMsg = "FalkorDB is offline or unreachable. Ingestion aborted.";
            job.status = "FAILED";
            job.currentPhase = "Failed";
            job.endTimeMillis = System.currentTimeMillis();
            job.addError(errorMsg);
            job.addLog("ERROR: " + errorMsg);
            return;
        }

        try {
            switch (job.provider) {
                case "gitlab" -> importGitLabProjects(job);
                case "bitbucket" -> importBitbucketRepositories(job);
                case "confluence" -> importConfluenceSpaces(job);
                default -> throw new IllegalArgumentException("Unsupported provider: " + job.provider);
            }

            job.status = job.errors.isEmpty() ? "COMPLETED" : "COMPLETED_WITH_WARNINGS";
            job.currentPhase = "Finished";
            job.endTimeMillis = System.currentTimeMillis();
            job.addLog("Job finished with " + job.itemsProcessed.get() + "/" + job.itemsTotal + " items processed, "
                    + job.commitsImported.get() + " commits, "
                    + job.pullRequestsImported.get() + " PRs/MRs, and "
                    + job.chunksCreated.get() + " vectorized document chunks.");

        } catch (Exception e) {
            log.error("Fatal error during {} import job {}: {}", job.provider, job.jobId, e.getMessage(), e);
            job.status = "FAILED";
            job.currentPhase = "Failed: " + e.getMessage();
            job.endTimeMillis = System.currentTimeMillis();
            job.addError("Fatal error: " + e.getMessage());
            job.addLog("FATAL ERROR: " + e.getMessage());
        }
    }

    // =========================================================================
    // GitLab Ingestion
    // =========================================================================

    private void importGitLabProjects(JobState job) {
        SourceIntegrationService.StoredCredential cred = sourceIntegrationService.getCredential("gitlab");
        String base = cred.baseUrl().replaceAll("/+$", "");
        if (base.endsWith("/api/v4")) {
            base = base.substring(0, base.length() - "/api/v4".length()).replaceAll("/+$", "");
        }

        for (String item : job.requestedItems) {
            job.currentItem = item;
            job.currentPhase = "Importing GitLab project: " + item;
            job.addLog("Processing GitLab project: " + item);

            try {
                String encoded = URLEncoder.encode(item, StandardCharsets.UTF_8);
                HttpResponse<String> projResp = sourceIntegrationService.executeAuthenticatedGet(
                        "gitlab", base + "/api/v4/projects/" + encoded
                );

                if (projResp.statusCode() != 200) {
                    String err = "GitLab project '" + item + "' returned HTTP " + projResp.statusCode();
                    job.addError(err);
                    job.addLog("ERROR: " + err);
                    continue;
                }

                JsonNode pNode = objectMapper.readTree(projResp.body());
                long projectId = pNode.path("id").asLong();
                String name = pNode.path("name").asText();
                String fullName = pNode.path("path_with_namespace").asText(item);
                String description = pNode.path("description").asText("");
                String webUrl = pNode.path("web_url").asText("");
                String defaultBranch = pNode.path("default_branch").asText("main");
                String updatedAt = pNode.path("last_activity_at").asText("");

                // 1. Persist Repository node in FalkorDB
                String repoSourceId = "gitlab:repo:" + fullName;
                String upsertCypher = """
                        MERGE (r:Repository {source_id: $sourceId})
                        ON CREATE SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt,
                          r.source = 'gitlab'
                        ON MATCH SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt,
                          r.source = 'gitlab'
                        """;

                graphClient.executeWrite(upsertCypher, Map.of(
                        "sourceId", repoSourceId,
                        "name", name,
                        "fullName", fullName,
                        "description", description,
                        "htmlUrl", webUrl,
                        "defaultBranch", defaultBranch,
                        "updatedAt", updatedAt
                ));

                // 2. Register overview Document and vectorize
                documentIngestionService.registerDocument(repoSourceId, "GitLab Project: " + fullName);
                String overview = "GitLab Project: " + fullName + "\n"
                        + "Description: " + (description.isBlank() ? "No description provided." : description) + "\n"
                        + "Default Branch: " + defaultBranch + "\n"
                        + "Web URL: " + webUrl + "\n"
                        + "Last Activity: " + updatedAt;

                DocumentIngestionResult overviewRes = documentIngestionService.ingestDocument(repoSourceId, overview);
                job.chunksCreated.addAndGet(overviewRes.chunkCount());
                job.addLog("Persisted project node and overview vector chunks for " + fullName);

                // 3. Fetch commits
                job.currentPhase = "Importing Commits for " + fullName;
                HttpResponse<String> commitsResp = sourceIntegrationService.executeAuthenticatedGet(
                        "gitlab", base + "/api/v4/projects/" + projectId + "/repository/commits?per_page=20"
                );
                if (commitsResp.statusCode() == 200) {
                    JsonNode cArr = objectMapper.readTree(commitsResp.body());
                    if (cArr.isArray()) {
                        String linkCypher = """
                                MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                                MERGE (r)-[:HAS_COMMIT]->(d)
                                """;
                        for (JsonNode c : cArr) {
                            String sha = c.path("id").asText();
                            String shortSha = c.path("short_id").asText(sha.length() > 7 ? sha.substring(0, 7) : sha);
                            String title = c.path("title").asText("Commit " + shortSha);
                            String authorName = c.path("author_name").asText();
                            String authorEmail = c.path("author_email").asText();
                            String createdAt = c.path("created_at").asText();
                            String message = c.path("message").asText();
                            String commitWebUrl = c.path("web_url").asText();

                            String commitSourceId = "gitlab:commit:" + fullName + "@" + shortSha;
                            documentIngestionService.registerDocument(commitSourceId, "Commit " + shortSha + ": " + title);
                            graphClient.executeWrite(linkCypher, Map.of(
                                    "repoSourceId", repoSourceId,
                                    "docSourceId", commitSourceId
                            ));

                            String commitContent = "GitLab Commit in " + fullName + " (" + sha + ")\n"
                                    + "Author: " + authorName + (authorEmail.isBlank() ? "" : " <" + authorEmail + ">") + "\n"
                                    + "Date: " + createdAt + "\n"
                                    + "URL: " + commitWebUrl + "\n"
                                    + "Message:\n" + message;

                            DocumentIngestionResult cRes = documentIngestionService.ingestDocument(commitSourceId, commitContent);
                            job.chunksCreated.addAndGet(cRes.chunkCount());
                            job.commitsImported.incrementAndGet();
                        }
                        job.addLog("Imported " + cArr.size() + " commits for " + fullName);
                    }
                }

                // 4. Fetch Merge Requests
                job.currentPhase = "Importing Merge Requests for " + fullName;
                HttpResponse<String> mrResp = sourceIntegrationService.executeAuthenticatedGet(
                        "gitlab", base + "/api/v4/projects/" + projectId + "/merge_requests?per_page=20"
                );
                if (mrResp.statusCode() == 200) {
                    JsonNode mrArr = objectMapper.readTree(mrResp.body());
                    if (mrArr.isArray()) {
                        String linkMrCypher = """
                                MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                                MERGE (r)-[:HAS_PR]->(d)
                                """;
                        for (JsonNode mr : mrArr) {
                            long iid = mr.path("iid").asLong();
                            String mrTitle = mr.path("title").asText();
                            String state = mr.path("state").asText();
                            String author = mr.path("author").path("username").asText();
                            String mrCreatedAt = mr.path("created_at").asText();
                            String mergedAt = mr.path("merged_at").asText();
                            String mrWebUrl = mr.path("web_url").asText();
                            String mrDesc = mr.path("description").asText("");

                            String mrSourceId = "gitlab:mr:" + fullName + "!" + iid;
                            documentIngestionService.registerDocument(mrSourceId, "MR !" + iid + ": " + mrTitle);
                            graphClient.executeWrite(linkMrCypher, Map.of(
                                    "repoSourceId", repoSourceId,
                                    "docSourceId", mrSourceId
                            ));

                            String mrContent = "GitLab Merge Request !" + iid + " in " + fullName + "\n"
                                    + "Title: " + mrTitle + "\n"
                                    + "State: " + state + "\n"
                                    + "Author: " + author + "\n"
                                    + "Created: " + mrCreatedAt + (mergedAt.isBlank() ? "" : "\nMerged: " + mergedAt) + "\n"
                                    + "URL: " + mrWebUrl + "\n"
                                    + "Description:\n" + (mrDesc.isBlank() ? "No description provided." : mrDesc);

                            DocumentIngestionResult mrRes = documentIngestionService.ingestDocument(mrSourceId, mrContent);
                            job.chunksCreated.addAndGet(mrRes.chunkCount());
                            job.pullRequestsImported.incrementAndGet();
                        }
                        job.addLog("Imported " + mrArr.size() + " merge requests for " + fullName);
                    }
                }

                job.importedItems.add(fullName);
                job.itemsProcessed.incrementAndGet();

            } catch (Exception e) {
                String err = "Failed to import GitLab project " + item + ": " + e.getMessage();
                job.addError(err);
                job.addLog("ERROR: " + err);
            }
        }
    }

    // =========================================================================
    // Bitbucket Ingestion
    // =========================================================================

    private void importBitbucketRepositories(JobState job) {
        SourceIntegrationService.StoredCredential cred = sourceIntegrationService.getCredential("bitbucket");
        String base = cred.baseUrl().replaceAll("/+$", "");
        if (base.equals("https://api.bitbucket.org")) {
            base = "https://api.bitbucket.org/2.0";
        }

        for (String item : job.requestedItems) {
            job.currentItem = item;
            job.currentPhase = "Importing Bitbucket repository: " + item;
            job.addLog("Processing Bitbucket repository: " + item);

            try {
                HttpResponse<String> repoResp = sourceIntegrationService.executeAuthenticatedGet(
                        "bitbucket", base + "/repositories/" + item
                );

                if (repoResp.statusCode() != 200) {
                    String err = "Bitbucket repository '" + item + "' returned HTTP " + repoResp.statusCode();
                    job.addError(err);
                    job.addLog("ERROR: " + err);
                    continue;
                }

                JsonNode rNode = objectMapper.readTree(repoResp.body());
                String name = rNode.path("name").asText();
                String fullName = rNode.path("full_name").asText(item);
                String description = rNode.path("description").asText("");
                String webUrl = rNode.path("links").path("html").path("href").asText("");
                String defaultBranch = rNode.path("mainbranch").path("name").asText("main");
                String updatedAt = rNode.path("updated_on").asText("");

                // 1. Persist Repository node in FalkorDB
                String repoSourceId = "bitbucket:repo:" + fullName;
                String upsertCypher = """
                        MERGE (r:Repository {source_id: $sourceId})
                        ON CREATE SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt,
                          r.source = 'bitbucket'
                        ON MATCH SET
                          r.name = $name,
                          r.full_name = $fullName,
                          r.description = $description,
                          r.html_url = $htmlUrl,
                          r.default_branch = $defaultBranch,
                          r.updated_at = $updatedAt,
                          r.source = 'bitbucket'
                        """;

                graphClient.executeWrite(upsertCypher, Map.of(
                        "sourceId", repoSourceId,
                        "name", name,
                        "fullName", fullName,
                        "description", description,
                        "htmlUrl", webUrl,
                        "defaultBranch", defaultBranch,
                        "updatedAt", updatedAt
                ));

                // 2. Register overview Document and vectorize
                documentIngestionService.registerDocument(repoSourceId, "Bitbucket Repository: " + fullName);
                String overview = "Bitbucket Repository: " + fullName + "\n"
                        + "Description: " + (description.isBlank() ? "No description provided." : description) + "\n"
                        + "Default Branch: " + defaultBranch + "\n"
                        + "Web URL: " + webUrl + "\n"
                        + "Last Updated: " + updatedAt;

                DocumentIngestionResult overviewRes = documentIngestionService.ingestDocument(repoSourceId, overview);
                job.chunksCreated.addAndGet(overviewRes.chunkCount());
                job.addLog("Persisted repository node and overview vector chunks for " + fullName);

                // 3. Fetch Commits
                job.currentPhase = "Importing Commits for " + fullName;
                HttpResponse<String> commitsResp = sourceIntegrationService.executeAuthenticatedGet(
                        "bitbucket", base + "/repositories/" + item + "/commits?pagelen=20"
                );
                if (commitsResp.statusCode() == 200) {
                    JsonNode cRoot = objectMapper.readTree(commitsResp.body());
                    JsonNode cArr = cRoot.path("values");
                    if (cArr.isArray()) {
                        String linkCypher = """
                                MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                                MERGE (r)-[:HAS_COMMIT]->(d)
                                """;
                        for (JsonNode c : cArr) {
                            String hash = c.path("hash").asText();
                            String shortHash = hash.length() > 7 ? hash.substring(0, 7) : hash;
                            String message = c.path("message").asText();
                            String firstLine = message.lines().findFirst().orElse("Commit " + shortHash);
                            String authorRaw = c.path("author").path("raw").asText();
                            String date = c.path("date").asText();
                            String commitWebUrl = c.path("links").path("html").path("href").asText();

                            String commitSourceId = "bitbucket:commit:" + fullName + "@" + shortHash;
                            documentIngestionService.registerDocument(commitSourceId, "Commit " + shortHash + ": " + firstLine);
                            graphClient.executeWrite(linkCypher, Map.of(
                                    "repoSourceId", repoSourceId,
                                    "docSourceId", commitSourceId
                            ));

                            String commitContent = "Bitbucket Commit in " + fullName + " (" + hash + ")\n"
                                    + "Author: " + authorRaw + "\n"
                                    + "Date: " + date + "\n"
                                    + "URL: " + commitWebUrl + "\n"
                                    + "Message:\n" + message;

                            DocumentIngestionResult cRes = documentIngestionService.ingestDocument(commitSourceId, commitContent);
                            job.chunksCreated.addAndGet(cRes.chunkCount());
                            job.commitsImported.incrementAndGet();
                        }
                        job.addLog("Imported " + cArr.size() + " commits for " + fullName);
                    }
                }

                // 4. Fetch Pull Requests
                job.currentPhase = "Importing Pull Requests for " + fullName;
                HttpResponse<String> prResp = sourceIntegrationService.executeAuthenticatedGet(
                        "bitbucket", base + "/repositories/" + item + "/pullrequests?state=ALL&pagelen=20"
                );
                if (prResp.statusCode() == 200) {
                    JsonNode prRoot = objectMapper.readTree(prResp.body());
                    JsonNode prArr = prRoot.path("values");
                    if (prArr.isArray()) {
                        String linkPrCypher = """
                                MATCH (r:Repository {source_id: $repoSourceId}), (d:Document {source_id: $docSourceId})
                                MERGE (r)-[:HAS_PR]->(d)
                                """;
                        for (JsonNode pr : prArr) {
                            long prId = pr.path("id").asLong();
                            String prTitle = pr.path("title").asText();
                            String state = pr.path("state").asText();
                            String author = pr.path("author").path("display_name").asText();
                            String prCreated = pr.path("created_on").asText();
                            String prWebUrl = pr.path("links").path("html").path("href").asText();
                            String prDesc = pr.path("description").asText("");

                            String prSourceId = "bitbucket:pr:" + fullName + "#" + prId;
                            documentIngestionService.registerDocument(prSourceId, "PR #" + prId + ": " + prTitle);
                            graphClient.executeWrite(linkPrCypher, Map.of(
                                    "repoSourceId", repoSourceId,
                                    "docSourceId", prSourceId
                            ));

                            String prContent = "Bitbucket Pull Request #" + prId + " in " + fullName + "\n"
                                    + "Title: " + prTitle + "\n"
                                    + "State: " + state + "\n"
                                    + "Author: " + author + "\n"
                                    + "Created: " + prCreated + "\n"
                                    + "URL: " + prWebUrl + "\n"
                                    + "Description:\n" + (prDesc.isBlank() ? "No description provided." : prDesc);

                            DocumentIngestionResult prRes = documentIngestionService.ingestDocument(prSourceId, prContent);
                            job.chunksCreated.addAndGet(prRes.chunkCount());
                            job.pullRequestsImported.incrementAndGet();
                        }
                        job.addLog("Imported " + prArr.size() + " pull requests for " + fullName);
                    }
                }

                job.importedItems.add(fullName);
                job.itemsProcessed.incrementAndGet();

            } catch (Exception e) {
                String err = "Failed to import Bitbucket repository " + item + ": " + e.getMessage();
                job.addError(err);
                job.addLog("ERROR: " + err);
            }
        }
    }

    // =========================================================================
    // Confluence Ingestion
    // =========================================================================

    private void importConfluenceSpaces(JobState job) {
        SourceIntegrationService.StoredCredential cred = sourceIntegrationService.getCredential("confluence");
        String base = cred.baseUrl().replaceAll("/+$", "");
        if (base.contains("atlassian.net") && !base.contains("/wiki")) {
            base = base + "/wiki";
        }

        for (String spaceKey : job.requestedItems) {
            job.currentItem = spaceKey;
            job.currentPhase = "Importing Confluence Space: " + spaceKey;
            job.addLog("Processing Confluence Space: " + spaceKey);

            try {
                HttpResponse<String> spaceResp = sourceIntegrationService.executeAuthenticatedGet(
                        "confluence", base + "/rest/api/space/" + spaceKey
                );

                String spaceName = spaceKey;
                String spaceDesc = "";
                String spaceUrl = base + "/spaces/" + spaceKey;

                if (spaceResp.statusCode() == 200) {
                    JsonNode sNode = objectMapper.readTree(spaceResp.body());
                    spaceName = sNode.path("name").asText(spaceKey);
                    spaceDesc = sNode.path("description").path("plain").path("value").asText("");
                    spaceUrl = base + sNode.path("_links").path("webui").asText("/spaces/" + spaceKey);
                }

                // 1. Persist Space node in FalkorDB
                String spaceSourceId = "confluence:space:" + spaceKey;
                String upsertSpaceCypher = """
                        MERGE (s:Space {source_id: $sourceId})
                        ON CREATE SET
                          s.name = $name,
                          s.key = $key,
                          s.description = $description,
                          s.html_url = $htmlUrl,
                          s.source = 'confluence'
                        ON MATCH SET
                          s.name = $name,
                          s.key = $key,
                          s.description = $description,
                          s.html_url = $htmlUrl,
                          s.source = 'confluence'
                        """;

                graphClient.executeWrite(upsertSpaceCypher, Map.of(
                        "sourceId", spaceSourceId,
                        "name", spaceName,
                        "key", spaceKey,
                        "description", spaceDesc,
                        "htmlUrl", spaceUrl
                ));

                // Ingest Space Overview Document
                documentIngestionService.registerDocument(spaceSourceId, "Confluence Space: " + spaceName);
                String spaceOverview = "Confluence Space: " + spaceName + " (" + spaceKey + ")\n"
                        + "Description: " + (spaceDesc.isBlank() ? "No description provided." : spaceDesc) + "\n"
                        + "URL: " + spaceUrl;

                DocumentIngestionResult sRes = documentIngestionService.ingestDocument(spaceSourceId, spaceOverview);
                job.chunksCreated.addAndGet(sRes.chunkCount());
                job.addLog("Persisted Confluence space node for " + spaceName);

                // 2. Fetch pages in space
                job.currentPhase = "Importing Pages for Space: " + spaceKey;
                HttpResponse<String> pagesResp = sourceIntegrationService.executeAuthenticatedGet(
                        "confluence", base + "/rest/api/content?type=page&spaceKey=" + spaceKey + "&limit=25&expand=body.storage,version,history"
                );

                if (pagesResp.statusCode() == 200) {
                    JsonNode pRoot = objectMapper.readTree(pagesResp.body());
                    JsonNode pArr = pRoot.path("results");
                    if (pArr.isArray()) {
                        String linkPageCypher = """
                                MATCH (s:Space {source_id: $spaceSourceId}), (d:Document {source_id: $docSourceId})
                                MERGE (s)-[:CONTAINS_PAGE]->(d)
                                """;

                        for (JsonNode page : pArr) {
                            String pageId = page.path("id").asText();
                            String title = page.path("title").asText();
                            String rawBody = page.path("body").path("storage").path("value").asText("");
                            String plainBody = stripHtml(rawBody);
                            String author = page.path("history").path("createdBy").path("displayName").asText("Author");
                            String versionDate = page.path("version").path("when").asText("");
                            String pageWebUrl = base + page.path("_links").path("webui").asText();

                            String pageSourceId = "confluence:page:" + pageId;
                            documentIngestionService.registerDocument(pageSourceId, "Confluence Page: " + title);
                            graphClient.executeWrite(linkPageCypher, Map.of(
                                    "spaceSourceId", spaceSourceId,
                                    "docSourceId", pageSourceId
                            ));

                            String pageContent = "Confluence Page: " + title + "\n"
                                    + "Space: " + spaceName + " (" + spaceKey + ")\n"
                                    + "Author: " + author + "\n"
                                    + "Last Updated: " + versionDate + "\n"
                                    + "URL: " + pageWebUrl + "\n\n"
                                    + plainBody;

                            DocumentIngestionResult pageDocRes = documentIngestionService.ingestDocument(pageSourceId, pageContent);
                            job.chunksCreated.addAndGet(pageDocRes.chunkCount());
                            job.pullRequestsImported.incrementAndGet(); // tracked as documents count
                        }
                        job.addLog("Imported " + pArr.size() + " pages for Confluence space " + spaceName);
                    }
                }

                job.importedItems.add(spaceName + " (" + spaceKey + ")");
                job.itemsProcessed.incrementAndGet();

            } catch (Exception e) {
                String err = "Failed to import Confluence space " + spaceKey + ": " + e.getMessage();
                job.addError(err);
                job.addLog("ERROR: " + err);
            }
        }
    }

    private static String stripHtml(String html) {
        if (html == null || html.isBlank()) return "";
        return html.replaceAll("(?i)<br\\s*/?>", "\n")
                .replaceAll("(?i)</p>", "\n\n")
                .replaceAll("(?i)</li>", "\n")
                .replaceAll("<[^>]*>", " ")
                .replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replaceAll("[ \t]+", " ")
                .replaceAll("\n{3,}", "\n\n")
                .trim();
    }

    // =========================================================================
    // In-Memory Job State
    // =========================================================================

    public static class JobState {
        public final String jobId;
        public final String provider;
        public final List<String> requestedItems;
        public final int itemsTotal;

        public volatile String status = "QUEUED";
        public volatile String currentPhase = "Queued";
        public volatile String currentItem = "";
        public final long startTimeMillis = System.currentTimeMillis();
        public volatile Long endTimeMillis = null;

        public final AtomicInteger itemsProcessed = new AtomicInteger(0);
        public final AtomicInteger commitsImported = new AtomicInteger(0);
        public final AtomicInteger pullRequestsImported = new AtomicInteger(0);
        public final AtomicInteger chunksCreated = new AtomicInteger(0);

        public final List<String> logs = new CopyOnWriteArrayList<>();
        public final List<String> errors = new CopyOnWriteArrayList<>();
        public final List<String> importedItems = new CopyOnWriteArrayList<>();

        public JobState(String jobId, String provider, List<String> requestedItems) {
            this.jobId = jobId;
            this.provider = provider;
            this.requestedItems = List.copyOf(requestedItems);
            this.itemsTotal = requestedItems.size();
        }

        public void addLog(String message) {
            logs.add("[" + java.time.Instant.now().toString() + "] " + message);
        }

        public void addError(String message) {
            errors.add(message);
        }

        public SourceJobStatusResponse toResponse() {
            return new SourceJobStatusResponse(
                    jobId,
                    provider,
                    status,
                    itemsTotal,
                    itemsProcessed.get(),
                    commitsImported.get(),
                    pullRequestsImported.get(),
                    chunksCreated.get(),
                    currentItem,
                    currentPhase,
                    startTimeMillis,
                    endTimeMillis,
                    List.copyOf(logs),
                    List.copyOf(errors),
                    List.copyOf(importedItems)
            );
        }
    }
}
