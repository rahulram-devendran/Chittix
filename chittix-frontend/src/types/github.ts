export interface GitHubStatusResponse {
  configured: boolean;
  username: string | null;
  name: string | null;
  avatarUrl: string | null;
  scopes: string[];
  rateLimitLimit: number;
  rateLimitRemaining: number;
  rateLimitResetEpochSeconds: number;
  message: string;
}

export interface GitHubRepo {
  id: number;
  name: string;
  fullName: string;
  description: string;
  htmlUrl: string;
  defaultBranch: string;
  isPrivate: boolean;
  stargazersCount: number;
  forksCount: number;
  openIssuesCount: number;
  updatedAt: string;
}

export interface GitHubJobStatus {
  jobId: string;
  status: 'QUEUED' | 'RUNNING' | 'COMPLETED' | 'COMPLETED_WITH_WARNINGS' | 'FAILED';
  repositoriesTotal: number;
  repositoriesProcessed: number;
  commitsImported: number;
  pullRequestsImported: number;
  chunksCreated: number;
  currentRepository: string;
  currentPhase: string;
  startTimeMillis: number;
  endTimeMillis: number | null;
  logs: string[];
  errors: string[];
  importedRepositories: string[];
}
