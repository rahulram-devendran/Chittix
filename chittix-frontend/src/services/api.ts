export interface HealthResponse {
  application: string;
  falkordb: string;
  graph: string;
  message: string;
}

export interface DecisionContext {
  decision: string;
  project: string;
  system: string;
  ownerTeam: string;
  documentTitle: string;
  sourceId: string;
  evidencePath: string[];
}

export interface PaymentsContextResponse {
  graph: string;
  count: number;
  contexts: DecisionContext[];
}

export interface GraphNode {
  id: string;
  name: string;
  type: string;
  sourceId?: string;
  properties?: Record<string, string>;
}

export interface GraphRelationship {
  id: string;
  source: string;
  target: string;
  type: string;
}

export interface GraphDataResponse {
  graph: string;
  nodeCount: number;
  relationshipCount: number;
  nodes: GraphNode[];
  relationships: GraphRelationship[];
}

export interface Evidence {
  documentTitle: string;
  sourceId: string;
}

export interface AskResponse {
  question: string;
  answered: boolean;
  answerType: string;
  answer: string;
  facts: string[];
  decisions: DecisionContext[];
  evidence: Evidence[];
  modelUsed?: string;
  providerUsed?: string;
  fallbackUsed?: boolean;
  fallbackReason?: string;
}

export interface ProviderInfo {
  provider: string;
  implemented: boolean;
  configured: boolean;
  model?: string;
  baseUrl?: string;
  apiKeyConfigured?: boolean;
  supportedCapabilities: string[];
}

export interface ProviderStatusResponse {
  selectedProvider: string;
  configuredModel: string;
  enabled: boolean;
  implemented: boolean;
  configured: boolean;
  baseUrl: string;
  supportedCapabilities: string[];
  availableProviders: ProviderInfo[];
}

const BASE_URL = import.meta.env.VITE_API_BASE_URL || '';

export async function checkHealth(): Promise<HealthResponse> {
  const res = await fetch(`${BASE_URL}/api/health`);
  if (!res.ok) {
    // 503 still returns HealthResponse JSON with falkordb: "DOWN"
    try {
      const data = await res.json();
      return data;
    } catch {
      throw new Error(`Health check failed with HTTP ${res.status}`);
    }
  }
  return res.json();
}

export async function getPaymentsContext(): Promise<PaymentsContextResponse> {
  const res = await fetch(`${BASE_URL}/api/demo/payments-context`);
  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`Failed to fetch payments context: HTTP ${res.status} ${errorText}`);
  }
  return res.json();
}

export async function getKnowledgeGraph(): Promise<GraphDataResponse> {
  const res = await fetch(`${BASE_URL}/api/brain/graph`);
  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`Failed to fetch knowledge graph: HTTP ${res.status} ${errorText}`);
  }
  return res.json();
}

export async function askQuestion(question: string): Promise<AskResponse> {
  const res = await fetch(`${BASE_URL}/api/brain/ask`, {
    method: 'POST',
    headers: {
      'Content-Type': 'application/json',
    },
    body: JSON.stringify({ question }),
  });
  if (!res.ok) {
    const errorText = await res.text().catch(() => '');
    throw new Error(`Question request failed: HTTP ${res.status} ${errorText}`);
  }
  return res.json();
}

export async function getProviderStatus(): Promise<ProviderStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/brain/provider-status`);
  if (!res.ok) {
    throw new Error(`Provider status failed with HTTP ${res.status}`);
  }
  return res.json();
}

export async function getGitHubStatus(): Promise<import('../types/github').GitHubStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/github/status`);
  if (!res.ok) {
    throw new Error(`GitHub status check failed: HTTP ${res.status}`);
  }
  return res.json();
}

export async function configureGitHub(token: string): Promise<import('../types/github').GitHubStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/github/configure`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ token }),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    const message = errorJson?.message || `Configuration failed with HTTP ${res.status}`;
    throw new Error(message);
  }
  return res.json();
}

export async function getGitHubRepos(): Promise<import('../types/github').GitHubRepo[]> {
  const res = await fetch(`${BASE_URL}/api/github/repos`);
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    const message = errorJson?.message || `HTTP ${res.status}`;
    throw new Error(message);
  }
  return res.json();
}

export async function startGitHubImport(repositories: string[]): Promise<{ jobId: string; status: string }> {
  const res = await fetch(`${BASE_URL}/api/github/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ repositories }),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    const message = errorJson?.message || `HTTP ${res.status}`;
    throw new Error(message);
  }
  return res.json();
}

export async function getGitHubJobStatus(jobId: string): Promise<import('../types/github').GitHubJobStatus> {
  const res = await fetch(`${BASE_URL}/api/github/jobs/${encodeURIComponent(jobId)}`);
  if (!res.ok) {
    throw new Error(`Failed to fetch job ${jobId}: HTTP ${res.status}`);
  }
  return res.json();
}

export async function listGitHubJobs(): Promise<import('../types/github').GitHubJobStatus[]> {
  const res = await fetch(`${BASE_URL}/api/github/jobs`);
  if (!res.ok) {
    throw new Error(`Failed to list import jobs: HTTP ${res.status}`);
  }
  return res.json();
}

export interface ProviderConfigRequest {
  provider: string;
  model?: string;
  baseUrl?: string;
  apiKey?: string;
}

export interface ProviderTestResult {
  success: boolean;
  provider: string;
  model?: string;
  message: string;
  latencyMs?: number;
}

export async function testProviderConnection(config: ProviderConfigRequest): Promise<ProviderTestResult> {
  const res = await fetch(`${BASE_URL}/api/brain/test-provider`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Test failed: HTTP ${res.status}`);
  }
  return res.json();
}

export async function configureProvider(config: ProviderConfigRequest): Promise<ProviderStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/brain/configure-provider`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Configuration failed: HTTP ${res.status}`);
  }
  return res.json();
}

export interface SourceStatusResponse {
  provider: string;
  configured: boolean;
  connected: boolean;
  username?: string;
  displayName?: string;
  baseUrl?: string;
  message: string;
  ingestionSupported: boolean;
  ingestionNotice: string;
}

export interface SourceConfigRequest {
  provider: string;
  token: string;
  username?: string;
  baseUrl?: string;
}

export async function getAllSourceStatuses(): Promise<Record<string, SourceStatusResponse>> {
  const res = await fetch(`${BASE_URL}/api/sources/status`);
  if (!res.ok) {
    throw new Error(`Failed to load source statuses: HTTP ${res.status}`);
  }
  return res.json();
}

export async function configureSource(config: SourceConfigRequest): Promise<SourceStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/sources/configure`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Source configuration failed with HTTP ${res.status}`);
  }
  return res.json();
}

export async function testSourceConnection(config: SourceConfigRequest): Promise<SourceStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/sources/test`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(config),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Connection test failed with HTTP ${res.status}`);
  }
  return res.json();
}

export interface SourceItemDto {
  id: string;
  name: string;
  fullName: string;
  description: string;
  webUrl: string;
  defaultBranch?: string;
  type: string;
  isPrivate: boolean;
  metadata?: Record<string, string>;
}

export interface SourceJobStatusResponse {
  jobId: string;
  provider: string;
  status: string; // QUEUED, RUNNING, COMPLETED, COMPLETED_WITH_WARNINGS, FAILED
  itemsTotal: number;
  itemsProcessed: number;
  commitsImported: number;
  pullRequestsImported: number;
  chunksCreated: number;
  currentItem?: string;
  currentPhase: string;
  startTimeMillis: number;
  endTimeMillis?: number;
  logs: string[];
  errors: string[];
  importedItems: string[];
}

export async function getSourceItems(provider: string): Promise<SourceItemDto[]> {
  const res = await fetch(`${BASE_URL}/api/sources/${provider}/items`);
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Failed to list items for ${provider}: HTTP ${res.status}`);
  }
  return res.json();
}

export async function startSourceImport(provider: string, items: string[]): Promise<{ jobId: string; status: string }> {
  const res = await fetch(`${BASE_URL}/api/sources/${provider}/import`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ provider, items }),
  });
  if (!res.ok) {
    const errorJson = await res.json().catch(() => null);
    throw new Error(errorJson?.message || `Failed to start import for ${provider}: HTTP ${res.status}`);
  }
  return res.json();
}

export async function getSourceJobStatus(jobId: string): Promise<SourceJobStatusResponse> {
  const res = await fetch(`${BASE_URL}/api/sources/jobs/${jobId}`);
  if (!res.ok) {
    throw new Error(`Failed to get job status: HTTP ${res.status}`);
  }
  return res.json();
}

export async function listSourceJobs(provider: string): Promise<SourceJobStatusResponse[]> {
  const res = await fetch(`${BASE_URL}/api/sources/${provider}/jobs`);
  if (!res.ok) {
    throw new Error(`Failed to list jobs for ${provider}: HTTP ${res.status}`);
  }
  return res.json();
}



