import React, { useState, useEffect, useCallback } from 'react';
import type { GitHubStatusResponse, GitHubRepo, GitHubJobStatus } from '../types/github';
import {
  getGitHubStatus,
  configureGitHub,
  getGitHubRepos,
  startGitHubImport,
  getGitHubJobStatus,
  listGitHubJobs,
  getAllSourceStatuses,
  configureSource,
  testSourceConnection,
  getSourceItems,
  startSourceImport,
  getSourceJobStatus,
  listSourceJobs,
  type SourceStatusResponse,
  type SourceItemDto,
  type SourceJobStatusResponse,
} from '../services/api';

interface SourceIntegrationProps {
  onNavigateToAsk: (query?: string) => void;
}

type ProviderType = 'github' | 'gitlab' | 'bitbucket' | 'confluence';

export const SourceIntegration: React.FC<SourceIntegrationProps> = ({ onNavigateToAsk }) => {
  const [activeProvider, setActiveProvider] = useState<ProviderType>('github');
  const [otherStatuses, setOtherStatuses] = useState<Record<string, SourceStatusResponse>>({});
  const [, setOtherLoading] = useState<boolean>(false);

  // Form states for non-GitHub providers
  const [otherTokenInput, setOtherTokenInput] = useState<string>('');
  const [otherShowToken, setOtherShowToken] = useState<boolean>(false);
  const [otherBaseUrlInput, setOtherBaseUrlInput] = useState<string>('');
  const [otherUsernameInput, setOtherUsernameInput] = useState<string>('');
  const [isOtherActionRunning, setIsOtherActionRunning] = useState<boolean>(false);
  const [otherActionError, setOtherActionError] = useState<string | null>(null);
  const [otherActionSuccess, setOtherActionSuccess] = useState<string | null>(null);

  // Discovered items & jobs for non-GitHub providers
  const [sourceItems, setSourceItems] = useState<SourceItemDto[]>([]);
  const [sourceItemsLoading, setSourceItemsLoading] = useState<boolean>(false);
  const [sourceItemsError, setSourceItemsError] = useState<string | null>(null);
  const [sourceSearchFilter, setSourceSearchFilter] = useState<string>('');
  const [selectedSourceItems, setSelectedSourceItems] = useState<Set<string>>(new Set());

  const [activeOtherJobId, setActiveOtherJobId] = useState<string | null>(null);
  const [activeOtherJob, setActiveOtherJob] = useState<SourceJobStatusResponse | null>(null);
  const [isStartingOtherImport, setIsStartingOtherImport] = useState<boolean>(false);
  const [startOtherImportError, setStartOtherImportError] = useState<string | null>(null);
  const [recentOtherJobs, setRecentOtherJobs] = useState<SourceJobStatusResponse[]>([]);
  const [showOtherLogs, setShowOtherLogs] = useState<boolean>(true);

  const [status, setStatus] = useState<GitHubStatusResponse | null>(null);
  const [statusLoading, setStatusLoading] = useState<boolean>(true);
  const [statusError, setStatusError] = useState<string | null>(null);

  const [tokenInput, setTokenInput] = useState<string>('');
  const [showToken, setShowToken] = useState<boolean>(false);
  const [isConnecting, setIsConnecting] = useState<boolean>(false);
  const [connectError, setConnectError] = useState<string | null>(null);
  const [connectSuccess, setConnectSuccess] = useState<string | null>(null);

  const [repos, setRepos] = useState<GitHubRepo[]>([]);
  const [reposLoading, setReposLoading] = useState<boolean>(false);
  const [reposError, setReposError] = useState<string | null>(null);
  const [searchFilter, setSearchFilter] = useState<string>('');
  const [selectedRepos, setSelectedRepos] = useState<Set<string>>(new Set());

  const [activeJobId, setActiveJobId] = useState<string | null>(null);
  const [activeJob, setActiveJob] = useState<GitHubJobStatus | null>(null);
  const [isStartingImport, setIsStartingImport] = useState<boolean>(false);
  const [startError, setStartError] = useState<string | null>(null);

  const [recentJobs, setRecentJobs] = useState<GitHubJobStatus[]>([]);
  const [showLogs, setShowLogs] = useState<boolean>(true);
  const [showScopeDetails, setShowScopeDetails] = useState<boolean>(false);

  // 1. Fetch accessible repositories
  const loadRepos = useCallback(async () => {
    setReposLoading(true);
    setReposError(null);
    try {
      const list = await getGitHubRepos();
      setRepos(list);
    } catch (err: any) {
      setReposError(err.message || 'Failed to fetch repositories from GitHub');
      setRepos([]);
    } finally {
      setReposLoading(false);
    }
  }, []);

  // 2. Fetch GitHub backend status
  const refreshStatus = useCallback(async () => {
    setStatusLoading(true);
    setStatusError(null);
    try {
      const res = await getGitHubStatus();
      setStatus(res);
      if (res.configured) {
        loadRepos();
      }
    } catch (err: any) {
      setStatusError(err.message || 'Failed to verify GitHub connection');
      setStatus(null);
    } finally {
      setStatusLoading(false);
    }
  }, [loadRepos]);


  // 3. Load recent jobs
  const refreshRecentJobs = useCallback(async () => {
    try {
      const jobs = await listGitHubJobs();
      setRecentJobs(jobs);
    } catch {
      // Non-critical background fetch
    }
  }, []);

  // 4. Configure GitHub token in memory
  const handleConnectGitHub = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = tokenInput.trim();
    if (!trimmed) {
      setConnectError('Please enter a GitHub Personal Access Token.');
      return;
    }
    setIsConnecting(true);
    setConnectError(null);
    setConnectSuccess(null);
    try {
      const res = await configureGitHub(trimmed);
      setStatus(res);
      setConnectSuccess(`Successfully connected to GitHub as @${res.username || 'user'}!`);
      setTokenInput(''); // Clear UI input after successful connection
      await loadRepos();
      refreshRecentJobs();
    } catch (err: any) {
      setConnectError(err.message || 'Failed to connect to GitHub with the provided token.');
    } finally {
      setIsConnecting(false);
    }
  };

  // 5. Load statuses for other sources (GitLab, Bitbucket, Confluence)
  const loadAllSourceStatuses = useCallback(async () => {
    setOtherLoading(true);
    try {
      const statuses = await getAllSourceStatuses();
      setOtherStatuses(statuses);
    } catch {
      // Non-critical background fetch
    } finally {
      setOtherLoading(false);
    }
  }, []);

  const loadSourceItemsForProvider = useCallback(async (provider: string) => {
    setSourceItemsLoading(true);
    setSourceItemsError(null);
    try {
      const items = await getSourceItems(provider);
      setSourceItems(items);
    } catch (err: any) {
      setSourceItemsError(err.message || `Failed to fetch items from ${provider}`);
      setSourceItems([]);
    } finally {
      setSourceItemsLoading(false);
    }
  }, []);

  const loadRecentOtherJobsForProvider = useCallback(async (provider: string) => {
    try {
      const jobs = await listSourceJobs(provider);
      setRecentOtherJobs(jobs);
    } catch {
      // Non-critical background fetch
    }
  }, []);

  const handleSelectProvider = (provider: ProviderType) => {
    setActiveProvider(provider);
    setOtherTokenInput('');
    setOtherActionError(null);
    setOtherActionSuccess(null);
    setSourceSearchFilter('');
    setSelectedSourceItems(new Set());
    if (provider !== 'github') {
      if (otherStatuses[provider]?.baseUrl) {
        setOtherBaseUrlInput(otherStatuses[provider].baseUrl || '');
      } else {
        setOtherBaseUrlInput('');
      }
      if (otherStatuses[provider]?.connected) {
        loadSourceItemsForProvider(provider);
        loadRecentOtherJobsForProvider(provider);
      }
    }
  };

  const handleTestOtherConnection = async () => {
    const trimmed = otherTokenInput.trim();
    if (!trimmed) {
      setOtherActionError('Please enter an access token or password to test connection.');
      return;
    }
    setIsOtherActionRunning(true);
    setOtherActionError(null);
    setOtherActionSuccess(null);
    try {
      const res = await testSourceConnection({
        provider: activeProvider,
        token: trimmed,
        baseUrl: otherBaseUrlInput.trim() || undefined,
        username: otherUsernameInput.trim() || undefined,
      });
      if (res.connected) {
        setOtherActionSuccess(`Connection test passed! Authenticated as ${res.username || res.displayName || 'user'}.`);
      } else {
        setOtherActionError(res.message || 'Connection test failed.');
      }
      loadAllSourceStatuses();
      if (res.connected) {
        loadSourceItemsForProvider(activeProvider);
      }
    } catch (err: any) {
      setOtherActionError(err.message || 'Connection test failed.');
    } finally {
      setIsOtherActionRunning(false);
    }
  };

  const handleConfigureOtherSource = async (e?: React.FormEvent) => {
    if (e) e.preventDefault();
    const trimmed = otherTokenInput.trim();
    if (!trimmed) {
      setOtherActionError('Please enter an access token or password.');
      return;
    }
    setIsOtherActionRunning(true);
    setOtherActionError(null);
    setOtherActionSuccess(null);
    try {
      const res = await configureSource({
        provider: activeProvider,
        token: trimmed,
        baseUrl: otherBaseUrlInput.trim() || undefined,
        username: otherUsernameInput.trim() || undefined,
      });
      if (res.connected) {
        setOtherActionSuccess(`Successfully connected to ${activeProvider.toUpperCase()} as ${res.username || res.displayName || 'user'}!`);
        setOtherTokenInput(''); // Clear secret from UI state
        loadSourceItemsForProvider(activeProvider);
        loadRecentOtherJobsForProvider(activeProvider);
      } else {
        setOtherActionError(res.message || `Failed to connect to ${activeProvider}.`);
      }
      loadAllSourceStatuses();
    } catch (err: any) {
      setOtherActionError(err.message || `Failed to configure ${activeProvider}.`);
    } finally {
      setIsOtherActionRunning(false);
    }
  };

  const toggleSourceItemSelection = (id: string) => {
    setSelectedSourceItems(prev => {
      const next = new Set(prev);
      if (next.has(id)) next.delete(id);
      else next.add(id);
      return next;
    });
  };

  const handleSelectAllSourceItems = () => {
    if (selectedSourceItems.size === filteredSourceItems.length) {
      setSelectedSourceItems(new Set());
    } else {
      setSelectedSourceItems(new Set(filteredSourceItems.map(i => i.id || i.fullName)));
    }
  };

  const handleStartOtherImport = async () => {
    if (selectedSourceItems.size === 0) return;
    setIsStartingOtherImport(true);
    setStartOtherImportError(null);
    try {
      const res = await startSourceImport(activeProvider, Array.from(selectedSourceItems));
      setActiveOtherJobId(res.jobId);
      const initial = await getSourceJobStatus(res.jobId);
      setActiveOtherJob(initial);
      loadRecentOtherJobsForProvider(activeProvider);
    } catch (err: any) {
      setStartOtherImportError(err.message || 'Failed to start import');
    } finally {
      setIsStartingOtherImport(false);
    }
  };

  useEffect(() => {
    refreshStatus();
    refreshRecentJobs();
    loadAllSourceStatuses();
  }, [refreshStatus, refreshRecentJobs, loadAllSourceStatuses]);

  // Poll active external source job status
  useEffect(() => {
    if (!activeOtherJobId) return;

    let isSubscribed = true;
    const pollInterval = setInterval(async () => {
      try {
        const job = await getSourceJobStatus(activeOtherJobId);
        if (!isSubscribed) return;
        setActiveOtherJob(job);

        if (job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_WARNINGS' || job.status === 'FAILED') {
          clearInterval(pollInterval);
          loadRecentOtherJobsForProvider(activeProvider);
        }
      } catch (err) {
        console.error('Error polling other source job status:', err);
      }
    }, 1500);

    return () => {
      isSubscribed = false;
      clearInterval(pollInterval);
    };
  }, [activeOtherJobId, activeProvider, loadRecentOtherJobsForProvider]);

  // 4. Poll active job status
  useEffect(() => {
    if (!activeJobId) return;

    let isSubscribed = true;
    const pollInterval = setInterval(async () => {
      try {
        const job = await getGitHubJobStatus(activeJobId);
        if (!isSubscribed) return;
        setActiveJob(job);

        if (job.status === 'COMPLETED' || job.status === 'COMPLETED_WITH_WARNINGS' || job.status === 'FAILED') {
          clearInterval(pollInterval);
          refreshRecentJobs();
        }
      } catch (err) {
        console.error('Error polling job status:', err);
      }
    }, 1500);

    return () => {
      isSubscribed = false;
      clearInterval(pollInterval);
    };
  }, [activeJobId, refreshRecentJobs]);

  // Repository selection helpers
  const toggleRepoSelection = (fullName: string) => {
    setSelectedRepos(prev => {
      const next = new Set(prev);
      if (next.has(fullName)) {
        next.delete(fullName);
      } else {
        next.add(fullName);
      }
      return next;
    });
  };

  const filteredRepos = repos.filter(
    r =>
      r.fullName.toLowerCase().includes(searchFilter.toLowerCase()) ||
      r.description?.toLowerCase().includes(searchFilter.toLowerCase())
  );

  const handleSelectAllFiltered = () => {
    setSelectedRepos(prev => {
      const next = new Set(prev);
      filteredRepos.forEach(r => next.add(r.fullName));
      return next;
    });
  };

  const handleClearSelection = () => {
    setSelectedRepos(new Set());
  };

  // Start import job
  const handleStartImport = async () => {
    if (selectedRepos.size === 0) return;
    setIsStartingImport(true);
    setStartError(null);
    try {
      const res = await startGitHubImport(Array.from(selectedRepos));
      setActiveJobId(res.jobId);
      // Immediately fetch first state
      const initialJob = await getGitHubJobStatus(res.jobId);
      setActiveJob(initialJob);
    } catch (err: any) {
      setStartError(err.message || 'Failed to start import job');
    } finally {
      setIsStartingImport(false);
    }
  };

  const isJobRunning = activeJob?.status === 'RUNNING' || activeJob?.status === 'QUEUED';
  const progressPercent = activeJob && activeJob.repositoriesTotal > 0
    ? Math.round((activeJob.repositoriesProcessed / activeJob.repositoriesTotal) * 100)
    : 0;

  const filteredSourceItems = sourceItems.filter(item => {
    if (!sourceSearchFilter.trim()) return true;
    const q = sourceSearchFilter.toLowerCase();
    return (item.name && item.name.toLowerCase().includes(q))
      || (item.fullName && item.fullName.toLowerCase().includes(q))
      || (item.description && item.description.toLowerCase().includes(q));
  });

  const isOtherJobRunning = activeOtherJob?.status === 'RUNNING' || activeOtherJob?.status === 'QUEUED';
  const otherProgressPercent = activeOtherJob && activeOtherJob.itemsTotal > 0
    ? Math.round((activeOtherJob.itemsProcessed / activeOtherJob.itemsTotal) * 100)
    : 0;

  return (
    <div className="max-w-6xl mx-auto px-4 md:px-8 py-6 space-y-6">
      {/* View Header */}
      <div className="flex flex-col md:flex-row md:items-center justify-between gap-4 pb-2 border-b border-outline-variant/30">
        <div>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[24px]">
              deployed_code
            </span>
            <h1 className="font-headline-sm text-xl font-bold tracking-tight text-primary">
              GitHub Sources & Ingestion
            </h1>
            <span className="font-code-sm text-[10px] px-2 py-0.5 rounded-full bg-secondary-container/30 text-secondary border border-secondary/30 font-semibold uppercase">
              V1 Metadata Slice
            </span>
          </div>
          <p className="font-body-sm text-xs text-on-surface-variant mt-1">
            Connect repositories to ingest metadata, commit provenance, and pull-request decisions directly into the FalkorDB knowledge graph.
          </p>
        </div>

        <button
          onClick={refreshStatus}
          disabled={statusLoading}
          className="inline-flex items-center gap-2 px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 text-xs font-label-md text-on-surface-variant hover:text-primary transition-all cursor-pointer self-start"
        >
          <span className={`material-symbols-outlined text-[16px] ${statusLoading ? 'animate-spin' : ''}`}>
            sync
          </span>
          {statusLoading ? 'Checking...' : 'Refresh Status'}
        </button>
      </div>

      {/* Provider Selector Cards */}
      <div className="grid grid-cols-2 sm:grid-cols-4 gap-3">
        <button
          type="button"
          onClick={() => handleSelectProvider('github')}
          className={`p-3.5 rounded-xl text-left flex flex-col gap-1 transition-all cursor-pointer ${
            activeProvider === 'github'
              ? 'bg-surface-container border-2 border-primary shadow-sm'
              : 'bg-surface-container-low border border-outline-variant/20 hover:bg-surface-container'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="font-bold text-sm text-primary flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[18px]">terminal</span>
              GitHub
            </span>
            <span className={`w-2 h-2 rounded-full ${status?.configured ? 'bg-tertiary animate-pulse' : 'bg-amber-400'}`} />
          </div>
          <span className={`font-code-sm text-[11px] font-medium ${status?.configured ? 'text-tertiary' : 'text-on-surface-variant'}`}>
            {status?.configured ? 'Connected (Verified)' : 'Ready to Connect'}
          </span>
        </button>

        <button
          type="button"
          onClick={() => handleSelectProvider('gitlab')}
          className={`p-3.5 rounded-xl text-left flex flex-col gap-1 transition-all cursor-pointer ${
            activeProvider === 'gitlab'
              ? 'bg-surface-container border-2 border-primary shadow-sm'
              : 'bg-surface-container-low border border-outline-variant/20 hover:bg-surface-container'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="font-bold text-sm text-on-surface-variant flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[18px]">alt_route</span>
              GitLab
            </span>
            <span className={`w-2 h-2 rounded-full ${otherStatuses['gitlab']?.connected ? 'bg-tertiary' : 'bg-outline/50'}`} />
          </div>
          <span className="font-code-sm text-[11px] text-on-surface-variant">
            {otherStatuses['gitlab']?.connected ? 'Connected (Verified)' : 'Ready to Connect'}
          </span>
        </button>

        <button
          type="button"
          onClick={() => handleSelectProvider('bitbucket')}
          className={`p-3.5 rounded-xl text-left flex flex-col gap-1 transition-all cursor-pointer ${
            activeProvider === 'bitbucket'
              ? 'bg-surface-container border-2 border-primary shadow-sm'
              : 'bg-surface-container-low border border-outline-variant/20 hover:bg-surface-container'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="font-bold text-sm text-on-surface-variant flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[18px]">inventory_2</span>
              Bitbucket
            </span>
            <span className={`w-2 h-2 rounded-full ${otherStatuses['bitbucket']?.connected ? 'bg-tertiary' : 'bg-outline/50'}`} />
          </div>
          <span className="font-code-sm text-[11px] text-on-surface-variant">
            {otherStatuses['bitbucket']?.connected ? 'Connected (Verified)' : 'Ready to Connect'}
          </span>
        </button>

        <button
          type="button"
          onClick={() => handleSelectProvider('confluence')}
          className={`p-3.5 rounded-xl text-left flex flex-col gap-1 transition-all cursor-pointer ${
            activeProvider === 'confluence'
              ? 'bg-surface-container border-2 border-primary shadow-sm'
              : 'bg-surface-container-low border border-outline-variant/20 hover:bg-surface-container'
          }`}
        >
          <div className="flex items-center justify-between">
            <span className="font-bold text-sm text-on-surface-variant flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[18px]">book</span>
              Confluence
            </span>
            <span className={`w-2 h-2 rounded-full ${otherStatuses['confluence']?.connected ? 'bg-tertiary' : 'bg-outline/50'}`} />
          </div>
          <span className="font-code-sm text-[11px] text-on-surface-variant">
            {otherStatuses['confluence']?.connected ? 'Connected (Verified)' : 'Ready to Connect'}
          </span>
        </button>
      </div>

      {activeProvider === 'github' ? (
        <>
          {/* Backend Authentication & Verification Status */}
      <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-4">
        <div className="flex items-center justify-between flex-wrap gap-2">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[20px]">
              verified_user
            </span>
            <span className="font-headline-sm text-sm font-bold text-primary">
              Backend Credential Status
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className={`inline-flex items-center gap-1.5 font-code-sm text-[11px] px-2.5 py-0.5 rounded-full font-semibold ${
              status?.configured
                ? 'bg-tertiary-container/30 text-tertiary border border-tertiary/30'
                : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
            }`}>
              <span className={`w-1.5 h-1.5 rounded-full ${status?.configured ? 'bg-tertiary' : 'bg-amber-400'}`} />
              {status?.configured ? 'Connected & Verified' : 'Token Not Configured'}
            </span>
          </div>
        </div>

        {statusError && (
          <div className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm">
            {statusError}
          </div>
        )}

        {statusLoading ? (
          <div className="flex items-center gap-2 text-xs text-on-surface-variant py-2">
            <span className="material-symbols-outlined text-[16px] animate-spin text-secondary">
              progress_activity
            </span>
            Verifying GitHub connection from backend...
          </div>
        ) : status?.configured ? (
          <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 pt-1">
            <div className="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-3">
              {status.avatarUrl ? (
                <img
                  src={status.avatarUrl}
                  alt={status.username || 'User'}
                  className="w-10 h-10 rounded-full border border-primary-container/40"
                />
              ) : (
                <div className="w-10 h-10 rounded-full bg-primary-container/30 flex items-center justify-center text-primary font-bold">
                  {status.username?.charAt(0).toUpperCase() || 'U'}
                </div>
              )}
              <div className="min-w-0 flex-1">
                <div className="font-semibold text-xs text-primary truncate">
                  {status.name || status.username}
                </div>
                <div className="font-code-sm text-[11px] text-on-surface-variant truncate">
                  @{status.username}
                </div>
              </div>
            </div>

            <div className="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col justify-center">
              <span className="font-code-sm text-[10px] uppercase text-outline font-semibold">
                OAuth Scopes
              </span>
              <div className="flex flex-wrap gap-1 mt-1">
                {status.scopes && status.scopes.length > 0 ? (
                  status.scopes.map(s => (
                    <span
                      key={s}
                      className="font-code-sm text-[10px] px-1.5 py-0.2 rounded bg-surface-container-high text-secondary"
                    >
                      {s}
                    </span>
                  ))
                ) : (
                  <span className="font-code-sm text-[11px] text-on-surface-variant">
                    Fine-grained / Public access
                  </span>
                )}
              </div>
            </div>

            <div className="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col justify-center">
              <span className="font-code-sm text-[10px] uppercase text-outline font-semibold">
                GitHub API Rate Limit
              </span>
              <div className="flex items-center justify-between mt-1 font-code-sm text-xs">
                <span className="font-semibold text-primary">
                  {status.rateLimitRemaining.toLocaleString()} / {status.rateLimitLimit.toLocaleString()}
                </span>
                <span className="text-[10px] text-outline">
                  {Math.round((status.rateLimitRemaining / (status.rateLimitLimit || 1)) * 100)}% remaining
                </span>
              </div>
            </div>
          </div>
        ) : null}

        {/* Token Configuration Card */}
        <div className="p-3.5 rounded-lg bg-surface-container border border-outline-variant/30 space-y-3">
          <div className="flex items-center justify-between flex-wrap gap-2">
            <label htmlFor="github-pat-input" className="font-label-md text-xs font-semibold text-primary flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[16px] text-secondary">key</span>
              <span>{status?.configured ? 'Update GitHub Access Token' : 'Configure GitHub Personal Access Token'}</span>
            </label>
            <span className="font-code-sm text-[10px] text-outline">
              In-memory only · Validated via HTTPS · Loopback restricted
            </span>
          </div>

          <form onSubmit={handleConnectGitHub} className="flex flex-col sm:flex-row items-stretch sm:items-center gap-2">
            <div className="relative flex-1">
              <input
                id="github-pat-input"
                data-testid="github-token-input"
                type={showToken ? 'text' : 'password'}
                value={tokenInput}
                onChange={e => {
                  setTokenInput(e.target.value);
                  if (connectError) setConnectError(null);
                }}
                placeholder={status?.configured ? 'Enter new PAT to replace active token (ghp_...)' : 'Enter GitHub Personal Access Token (ghp_...)'}
                autoComplete="off"
                disabled={isConnecting}
                className="w-full pl-3 pr-10 py-2 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:border-primary disabled:opacity-50 transition-colors"
              />
              <button
                type="button"
                onClick={() => setShowToken(!showToken)}
                disabled={isConnecting}
                className="absolute right-2 top-2 text-outline hover:text-primary transition-colors p-0.5 cursor-pointer disabled:opacity-50"
                title={showToken ? 'Hide token' : 'Show token'}
                aria-label={showToken ? 'Hide token' : 'Show token'}
              >
                <span className="material-symbols-outlined text-[18px]">
                  {showToken ? 'visibility_off' : 'visibility'}
                </span>
              </button>
            </div>

            <button
              type="submit"
              data-testid="github-connect-button"
              disabled={isConnecting || !tokenInput.trim()}
              className="inline-flex items-center justify-center gap-2 px-4 py-2 rounded-lg bg-primary hover:bg-primary/90 text-on-primary font-label-md text-xs font-semibold shadow-sm transition-all active:scale-95 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed flex-shrink-0"
            >
              <span className={`material-symbols-outlined text-[16px] ${isConnecting ? 'animate-spin' : ''}`}>
                {isConnecting ? 'sync' : 'link'}
              </span>
              <span>{isConnecting ? 'Validating...' : status?.configured ? 'Update Token' : 'Connect GitHub'}</span>
            </button>
          </form>

          {connectError && (
            <div data-testid="github-connect-error" className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm flex items-start gap-2">
              <span className="material-symbols-outlined text-[16px] flex-shrink-0 mt-0.5">error</span>
              <span className="flex-1">{connectError}</span>
            </div>
          )}

          {connectSuccess && (
            <div data-testid="github-connect-success" className="p-3 rounded-lg bg-tertiary-container/20 border border-tertiary/40 text-xs text-tertiary font-body-sm flex items-center justify-between">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[16px]">check_circle</span>
                <span>{connectSuccess}</span>
              </div>
              <button
                type="button"
                onClick={() => setConnectSuccess(null)}
                className="text-tertiary hover:opacity-80 p-0.5 cursor-pointer"
              >
                <span className="material-symbols-outlined text-[14px]">close</span>
              </button>
            </div>
          )}

          {!status?.configured && (
            <div className="text-[11px] text-outline pt-1 flex items-center gap-1.5">
              <span className="material-symbols-outlined text-[14px] text-secondary">info</span>
              <span>
                Recommended scopes: <strong>repo</strong> (or <strong>public_repo</strong> for public repositories). Tokens are held in backend memory only and never saved to disk, database, or browser storage.
              </span>
            </div>
          )}
        </div>
      </div>

      {/* Scope Details Accordion */}
      <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30">
        <button
          onClick={() => setShowScopeDetails(prev => !prev)}
          className="w-full flex items-center justify-between text-left cursor-pointer"
        >
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[20px]">
              rule
            </span>
            <span className="font-headline-sm text-sm font-bold text-primary">
              V1 Metadata Import Scope & Limits Disclosure
            </span>
          </div>
          <span className="material-symbols-outlined text-outline text-[20px]">
            {showScopeDetails ? 'expand_less' : 'expand_more'}
          </span>
        </button>

        {showScopeDetails && (
          <div className="mt-3 pt-3 border-t border-outline-variant/20 grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            <div className="space-y-1.5 p-3 rounded-lg bg-surface-container">
              <div className="font-semibold text-tertiary flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[16px]">check_circle</span>
                Included in V1 Ingestion
              </div>
              <ul className="list-disc list-inside space-y-1 text-on-surface-variant font-body-sm pl-1">
                <li>Repository metadata (name, description, default branch, URLs)</li>
                <li>Commit metadata (author, timestamp, commit message, SHA) — <strong>capped at 20 recent commits</strong></li>
                <li>Pull Request metadata (author, state, created/merged dates, review states) — <strong>capped at 20 recent PRs</strong></li>
                <li>Vector embeddings (384-d MiniLM-L6) generated for each document chunk in FalkorDB</li>
                <li>Searchable in <strong>Ask & Investigate</strong> using natural language questions</li>
              </ul>
            </div>

            <div className="space-y-1.5 p-3 rounded-lg bg-surface-container">
              <div className="font-semibold text-outline flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[16px]">cancel</span>
                Excluded / Guarded in V1
              </div>
              <ul className="list-disc list-inside space-y-1 text-on-surface-variant font-body-sm pl-1">
                <li>Raw source code bulk ingestion (no full repo cloning)</li>
                <li>Large unified diffs or binary assets</li>
                <li>Fabricated relationships, ownership, or fake business decisions</li>
                <li>In-memory job tracker: job logs are held in backend RAM and reset on server restart</li>
              </ul>
            </div>
          </div>
        )}
      </div>

      {/* Active Job Real-Time Progress Monitor */}
      {activeJob && (
        <div className={`p-4 rounded-xl border ${
          activeJob.status === 'RUNNING' || activeJob.status === 'QUEUED'
            ? 'bg-primary-container/10 border-primary-container/40'
            : activeJob.status === 'COMPLETED'
            ? 'bg-tertiary-container/10 border-tertiary-container/40'
            : activeJob.status === 'COMPLETED_WITH_WARNINGS'
            ? 'bg-amber-500/10 border-amber-500/30'
            : 'bg-error-container/10 border-error-container/40'
        } space-y-3`}>
          <div className="flex items-center justify-between flex-wrap gap-2">
            <div className="flex items-center gap-2">
              <span className={`material-symbols-outlined text-[20px] ${
                isJobRunning ? 'text-primary animate-spin' : activeJob.status === 'COMPLETED' ? 'text-tertiary' : 'text-error'
              }`}>
                {isJobRunning ? 'sync' : activeJob.status === 'COMPLETED' ? 'check_circle' : 'error'}
              </span>
              <span className="font-headline-sm text-sm font-bold text-primary">
                Import Job: {activeJob.jobId}
              </span>
              <span className={`font-code-sm text-[10px] px-2 py-0.5 rounded-full font-bold ${
                activeJob.status === 'RUNNING'
                  ? 'bg-primary-container/30 text-primary'
                  : activeJob.status === 'COMPLETED'
                  ? 'bg-tertiary-container/30 text-tertiary'
                  : activeJob.status === 'COMPLETED_WITH_WARNINGS'
                  ? 'bg-amber-500/20 text-amber-400'
                  : 'bg-error-container/30 text-error'
              }`}>
                {activeJob.status}
              </span>
            </div>

            {activeJob.status === 'COMPLETED' && (
              <button
                onClick={() => onNavigateToAsk(activeJob.importedRepositories[0] ? `What commits and pull requests were imported for ${activeJob.importedRepositories[0]}?` : 'What GitHub evidence is in the knowledge base?')}
                className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-tertiary-container/30 hover:bg-tertiary-container/50 border border-tertiary/40 text-xs font-label-md text-tertiary cursor-pointer transition-all"
              >
                <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                Explore in Ask & Investigate
              </button>
            )}
          </div>

          {/* Progress Bar */}
          <div className="space-y-1">
            <div className="flex items-center justify-between text-xs font-code-sm">
              <span className="text-on-surface-variant font-medium">
                Phase: <strong className="text-primary">{activeJob.currentPhase}</strong>
              </span>
              <span className="text-secondary font-semibold">
                {progressPercent}%
              </span>
            </div>
            <div className="w-full h-2 rounded-full bg-surface-container overflow-hidden">
              <div
                className={`h-full transition-all duration-300 ${
                  activeJob.status === 'FAILED' ? 'bg-error' : 'bg-primary'
                }`}
                style={{ width: `${progressPercent}%` }}
              />
            </div>
          </div>

          {/* Metric Badges */}
          <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1 font-code-sm text-xs">
            <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
              <span className="text-[10px] text-outline uppercase font-semibold">Repositories</span>
              <span className="font-bold text-primary mt-0.5">
                {activeJob.repositoriesProcessed} / {activeJob.repositoriesTotal}
              </span>
            </div>

            <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
              <span className="text-[10px] text-outline uppercase font-semibold">Commits Imported</span>
              <span className="font-bold text-secondary mt-0.5">
                {activeJob.commitsImported}
              </span>
            </div>

            <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
              <span className="text-[10px] text-outline uppercase font-semibold">PRs & Reviews</span>
              <span className="font-bold text-tertiary mt-0.5">
                {activeJob.pullRequestsImported}
              </span>
            </div>

            <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
              <span className="text-[10px] text-outline uppercase font-semibold">Vector Chunks</span>
              <span className="font-bold text-primary mt-0.5">
                {activeJob.chunksCreated}
              </span>
            </div>
          </div>

          {/* Error List */}
          {activeJob.errors && activeJob.errors.length > 0 && (
            <div className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error space-y-1">
              <div className="font-bold flex items-center gap-1">
                <span className="material-symbols-outlined text-[16px]">warning</span>
                Job encountered errors:
              </div>
              <ul className="list-disc list-inside space-y-0.5 font-code-sm pl-1">
                {activeJob.errors.map((e, idx) => (
                  <li key={idx}>{e}</li>
                ))}
              </ul>
            </div>
          )}

          {/* Real-time Logs Box */}
          <div className="space-y-1.5 pt-1">
            <div className="flex items-center justify-between">
              <span className="font-code-sm text-[11px] text-outline font-semibold uppercase">
                Execution Logs ({activeJob.logs.length})
              </span>
              <button
                onClick={() => setShowLogs(prev => !prev)}
                className="font-code-sm text-[10px] text-secondary hover:underline cursor-pointer"
              >
                {showLogs ? 'Hide Logs' : 'Show Logs'}
              </button>
            </div>

            {showLogs && (
              <div className="p-3 rounded-lg bg-surface-container-lowest border border-outline-variant/30 font-code-sm text-[11px] text-on-surface-variant max-h-48 overflow-y-auto space-y-1 font-mono">
                {activeJob.logs.map((logLine, idx) => (
                  <div key={idx} className="leading-tight">
                    {logLine}
                  </div>
                ))}
              </div>
            )}
          </div>
        </div>
      )}

      {/* Repository Selection & Action Section */}
      <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-4">
        <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[20px]">
              folder_open
            </span>
            <span className="font-headline-sm text-sm font-bold text-primary">
              Accessible Repositories
            </span>
            <span className="font-code-sm text-xs text-outline font-semibold">
              ({filteredRepos.length} available)
            </span>
          </div>

          <div className="flex items-center gap-2">
            <button
              onClick={handleSelectAllFiltered}
              disabled={filteredRepos.length === 0 || isJobRunning}
              className="px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high border border-outline-variant/20 font-label-sm text-xs text-on-surface-variant hover:text-primary transition-colors cursor-pointer disabled:opacity-50"
            >
              Select All
            </button>
            <button
              onClick={handleClearSelection}
              disabled={selectedRepos.size === 0 || isJobRunning}
              className="px-2.5 py-1 rounded bg-surface-container hover:bg-surface-container-high border border-outline-variant/20 font-label-sm text-xs text-on-surface-variant hover:text-primary transition-colors cursor-pointer disabled:opacity-50"
            >
              Clear
            </button>

            <button
              onClick={handleStartImport}
              disabled={selectedRepos.size === 0 || isJobRunning || isStartingImport}
              className="inline-flex items-center gap-2 px-4 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary font-label-md text-xs font-semibold shadow-sm transition-all active:scale-95 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
            >
              <span className={`material-symbols-outlined text-[16px] ${isStartingImport ? 'animate-spin' : ''}`}>
                {isStartingImport ? 'sync' : 'cloud_download'}
              </span>
              {isStartingImport
                ? 'Starting...'
                : `Import Selected (${selectedRepos.size})`}
            </button>
          </div>
        </div>

        {/* Search Input */}
        <div className="relative">
          <span className="material-symbols-outlined absolute left-3 top-2.5 text-[18px] text-outline">
            search
          </span>
          <input
            type="text"
            value={searchFilter}
            onChange={e => setSearchFilter(e.target.value)}
            placeholder="Filter repositories by name or organization..."
            className="w-full pl-9 pr-3 py-2 rounded-lg bg-surface-container border border-outline-variant/30 text-xs font-body-sm text-on-surface placeholder:text-outline focus:outline-none focus:border-primary transition-colors"
          />
        </div>

        {startError && (
          <div className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm">
            {startError}
          </div>
        )}

        {/* Repositories List */}
        {reposLoading ? (
          <div className="py-8 text-center text-xs text-on-surface-variant flex flex-col items-center gap-2">
            <span className="material-symbols-outlined text-[24px] animate-spin text-secondary">
              progress_activity
            </span>
            Fetching repositories from GitHub...
          </div>
        ) : reposError ? (
          <div className="p-4 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error space-y-1">
            <div className="font-bold">Failed to load repositories:</div>
            <div>{reposError}</div>
          </div>
        ) : filteredRepos.length === 0 ? (
          <div className="py-8 text-center text-xs text-on-surface-variant">
            {status?.configured
              ? 'No repositories match the current search filter.'
              : 'GitHub repository access is not configured. Check your connection in GitHub Sources.'}
          </div>
        ) : (
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3 max-h-96 overflow-y-auto pr-1">
            {filteredRepos.map(repo => {
              const isSelected = selectedRepos.has(repo.fullName);
              return (
                <div
                  key={repo.id}
                  onClick={() => !isJobRunning && toggleRepoSelection(repo.fullName)}
                  className={`p-3 rounded-xl border text-xs flex flex-col justify-between transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-primary-container/15 border-primary-container shadow-sm'
                      : 'bg-surface-container hover:bg-surface-container-high border-outline-variant/20'
                  }`}
                >
                  <div className="space-y-1.5">
                    <div className="flex items-start justify-between gap-2">
                      <div className="flex items-center gap-2 min-w-0">
                        <input
                          type="checkbox"
                          checked={isSelected}
                          onChange={() => {}} // Handled by card click
                          disabled={isJobRunning}
                          className="rounded text-primary focus:ring-0 cursor-pointer"
                        />
                        <span className="font-semibold text-primary truncate" title={repo.fullName}>
                          {repo.fullName}
                        </span>
                      </div>
                      <span className={`font-code-sm text-[9px] px-1.5 py-0.2 rounded font-medium flex-shrink-0 ${
                        repo.isPrivate
                          ? 'bg-amber-500/20 text-amber-400'
                          : 'bg-surface-container-highest text-outline'
                      }`}>
                        {repo.isPrivate ? 'private' : 'public'}
                      </span>
                    </div>

                    <p className="font-body-sm text-[11px] text-on-surface-variant line-clamp-2">
                      {repo.description || 'No description provided.'}
                    </p>
                  </div>

                  <div className="flex items-center justify-between text-[10px] font-code-sm text-outline pt-2 mt-2 border-t border-outline-variant/15">
                    <div className="flex items-center gap-2">
                      <span className="flex items-center gap-0.5">
                        <span className="material-symbols-outlined text-[12px]">star</span>
                        {repo.stargazersCount}
                      </span>
                      <span className="flex items-center gap-0.5">
                        <span className="material-symbols-outlined text-[12px]">fork_right</span>
                        {repo.forksCount}
                      </span>
                    </div>
                    <span>{repo.defaultBranch}</span>
                  </div>
                </div>
              );
            })}
          </div>
        )}
      </div>

      {/* Recent Import History */}
      {recentJobs.length > 0 && (
        <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-secondary text-[20px]">
                history
              </span>
              <span className="font-headline-sm text-sm font-bold text-primary">
                Recent Ingestion Jobs (In-Memory)
              </span>
            </div>
            <button
              onClick={refreshRecentJobs}
              className="text-xs text-secondary hover:underline cursor-pointer"
            >
              Refresh
            </button>
          </div>

          <div className="divide-y divide-outline-variant/20 font-code-sm text-xs">
            {recentJobs.map(job => (
              <div
                key={job.jobId}
                onClick={() => {
                  setActiveJobId(job.jobId);
                  setActiveJob(job);
                }}
                className="py-2.5 flex items-center justify-between gap-3 hover:bg-surface-container/50 px-2 rounded cursor-pointer transition-colors"
              >
                <div className="flex items-center gap-2 min-w-0">
                  <span className={`w-2 h-2 rounded-full flex-shrink-0 ${
                    job.status === 'COMPLETED'
                      ? 'bg-tertiary'
                      : job.status === 'RUNNING'
                      ? 'bg-primary animate-pulse'
                      : job.status === 'COMPLETED_WITH_WARNINGS'
                      ? 'bg-amber-400'
                      : 'bg-error'
                  }`} />
                  <span className="font-semibold text-primary">{job.jobId}</span>
                  <span className="text-[11px] text-on-surface-variant truncate hidden sm:inline">
                    {job.importedRepositories.join(', ') || `${job.repositoriesTotal} repo(s)`}
                  </span>
                </div>

                <div className="flex items-center gap-3 text-[11px] text-outline flex-shrink-0">
                  <span>
                    {job.repositoriesProcessed}/{job.repositoriesTotal} repos
                  </span>
                  <span>•</span>
                  <span>{job.chunksCreated} chunks</span>
                  <span className={`font-semibold uppercase text-[10px] px-1.5 py-0.2 rounded ${
                    job.status === 'COMPLETED'
                      ? 'bg-tertiary-container/30 text-tertiary'
                      : job.status === 'RUNNING'
                      ? 'bg-primary-container/30 text-primary'
                      : 'bg-error-container/30 text-error'
                  }`}>
                    {job.status}
                  </span>
                </div>
              </div>
            ))}
          </div>
        </div>
      )}
        </>
      ) : (
        <div className="space-y-4">
          {/* Non-GitHub Provider Status Card */}
          <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-4">
            <div className="flex items-center justify-between flex-wrap gap-2">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-secondary text-[20px]">
                  {activeProvider === 'gitlab' ? 'alt_route' : activeProvider === 'bitbucket' ? 'inventory_2' : 'book'}
                </span>
                <span className="font-headline-sm text-sm font-bold text-primary capitalize">
                  {activeProvider} Connection Status
                </span>
              </div>
              <div className="flex items-center gap-2">
                <span className={`inline-flex items-center gap-1.5 font-code-sm text-[11px] px-2.5 py-0.5 rounded-full font-semibold ${
                  otherStatuses[activeProvider]?.connected
                    ? 'bg-tertiary-container/30 text-tertiary border border-tertiary/30'
                    : 'bg-amber-500/20 text-amber-400 border border-amber-500/30'
                }`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${otherStatuses[activeProvider]?.connected ? 'bg-tertiary' : 'bg-amber-400'}`} />
                  {otherStatuses[activeProvider]?.connected ? 'Connected & Verified' : 'Not Connected'}
                </span>
              </div>
            </div>

            {otherStatuses[activeProvider]?.connected && (
              <div className="p-3 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-wrap items-center justify-between gap-3 text-xs">
                <div className="flex items-center gap-2.5">
                  <div className="w-8 h-8 rounded-full bg-primary-container/30 flex items-center justify-center text-primary font-bold">
                    {(otherStatuses[activeProvider]?.username || otherStatuses[activeProvider]?.displayName || activeProvider).charAt(0).toUpperCase()}
                  </div>
                  <div>
                    <div className="font-semibold text-primary">
                      {otherStatuses[activeProvider]?.displayName || otherStatuses[activeProvider]?.username || 'Authenticated User'}
                    </div>
                    <div className="font-code-sm text-[11px] text-on-surface-variant">
                      @{otherStatuses[activeProvider]?.username || 'user'} · {otherStatuses[activeProvider]?.baseUrl || 'Default Endpoint'}
                    </div>
                  </div>
                </div>
                <span className="font-code-sm text-[10px] text-tertiary bg-tertiary-container/20 px-2 py-0.5 rounded border border-tertiary/30">
                  Validated via HTTPS API
                </span>
              </div>
            )}

            {/* Credential Configuration Form */}
            <div className="p-3.5 rounded-lg bg-surface-container border border-outline-variant/30 space-y-3">
              <div className="flex items-center justify-between flex-wrap gap-2">
                <label className="font-label-md text-xs font-semibold text-primary flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px] text-secondary">key</span>
                  <span>Configure {activeProvider.toUpperCase()} Credentials</span>
                </label>
                <span className="font-code-sm text-[10px] text-outline">
                  In-memory only · Verified via HTTPS · Loopback restricted
                </span>
              </div>

              <form onSubmit={handleConfigureOtherSource} className="space-y-3">
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  <div>
                    <label className="block text-[11px] font-medium text-on-surface-variant mb-1">
                      Base / Host URL (Optional)
                    </label>
                    <input
                      type="text"
                      value={otherBaseUrlInput}
                      onChange={e => setOtherBaseUrlInput(e.target.value)}
                      placeholder={
                        activeProvider === 'gitlab'
                          ? 'https://gitlab.com'
                          : activeProvider === 'bitbucket'
                          ? 'https://api.bitbucket.org'
                          : 'https://your-domain.atlassian.net/wiki'
                      }
                      disabled={isOtherActionRunning}
                      className="w-full px-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:border-primary disabled:opacity-50"
                    />
                  </div>

                  <div>
                    <label className="block text-[11px] font-medium text-on-surface-variant mb-1">
                      Username / Email {activeProvider === 'gitlab' ? '(Optional)' : '(Required)'}
                    </label>
                    <input
                      type="text"
                      value={otherUsernameInput}
                      onChange={e => setOtherUsernameInput(e.target.value)}
                      placeholder={
                        activeProvider === 'confluence'
                          ? 'user@company.com'
                          : activeProvider === 'bitbucket'
                          ? 'bitbucket-username'
                          : 'optional username'
                      }
                      disabled={isOtherActionRunning}
                      className="w-full px-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:border-primary disabled:opacity-50"
                    />
                  </div>
                </div>

                <div>
                  <label className="block text-[11px] font-medium text-on-surface-variant mb-1">
                    Personal Access Token / App Password / API Token
                  </label>
                  <div className="relative">
                    <input
                      type={otherShowToken ? 'text' : 'password'}
                      value={otherTokenInput}
                      onChange={e => {
                        setOtherTokenInput(e.target.value);
                        if (otherActionError) setOtherActionError(null);
                      }}
                      placeholder={
                        activeProvider === 'gitlab'
                          ? 'glpat-... (GitLab Personal Access Token with read_api scope)'
                          : activeProvider === 'bitbucket'
                          ? 'Bitbucket App Password with account:read scope'
                          : 'Atlassian API Token with read:confluence-space scope'
                      }
                      disabled={isOtherActionRunning}
                      className="w-full pl-3 pr-10 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:border-primary disabled:opacity-50"
                    />
                    <button
                      type="button"
                      onClick={() => setOtherShowToken(!otherShowToken)}
                      className="absolute right-2 top-1.5 text-outline hover:text-primary p-0.5 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[16px]">
                        {otherShowToken ? 'visibility_off' : 'visibility'}
                      </span>
                    </button>
                  </div>
                </div>

                {otherActionError && (
                  <div className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm flex items-start gap-2">
                    <span className="material-symbols-outlined text-[16px] flex-shrink-0 mt-0.5">error</span>
                    <span className="flex-1">{otherActionError}</span>
                  </div>
                )}

                {otherActionSuccess && (
                  <div className="p-3 rounded-lg bg-tertiary-container/20 border border-tertiary/40 text-xs text-tertiary font-body-sm flex items-center justify-between">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[16px]">check_circle</span>
                      <span>{otherActionSuccess}</span>
                    </div>
                    <button
                      type="button"
                      onClick={() => setOtherActionSuccess(null)}
                      className="text-tertiary hover:opacity-80 p-0.5 cursor-pointer"
                    >
                      <span className="material-symbols-outlined text-[14px]">close</span>
                    </button>
                  </div>
                )}

                <div className="flex items-center gap-2 pt-1">
                  <button
                    type="button"
                    onClick={handleTestOtherConnection}
                    disabled={isOtherActionRunning || !otherTokenInput.trim()}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-container-highest border border-outline-variant/30 text-xs font-semibold text-primary cursor-pointer disabled:opacity-50"
                  >
                    <span className={`material-symbols-outlined text-[14px] ${isOtherActionRunning ? 'animate-spin' : ''}`}>
                      {isOtherActionRunning ? 'sync' : 'network_check'}
                    </span>
                    <span>Test Connection</span>
                  </button>

                  <button
                    type="submit"
                    disabled={isOtherActionRunning || !otherTokenInput.trim()}
                    className="inline-flex items-center gap-1.5 px-4 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary text-xs font-semibold shadow-sm cursor-pointer disabled:opacity-50"
                  >
                    <span className="material-symbols-outlined text-[14px]">link</span>
                    <span>Connect &amp; Save</span>
                  </button>
                </div>
              </form>
            </div>

          </div>

          {/* Active Job Real-Time Progress Monitor for other provider */}
          {activeOtherJob && (
            <div className={`p-4 rounded-xl border ${
              activeOtherJob.status === 'RUNNING' || activeOtherJob.status === 'QUEUED'
                ? 'bg-primary-container/10 border-primary-container/40'
                : activeOtherJob.status === 'COMPLETED'
                ? 'bg-tertiary-container/10 border-tertiary-container/40'
                : activeOtherJob.status === 'COMPLETED_WITH_WARNINGS'
                ? 'bg-amber-500/10 border-amber-500/30'
                : 'bg-error-container/10 border-error-container/40'
            } space-y-3`}>
              <div className="flex items-center justify-between flex-wrap gap-2">
                <div className="flex items-center gap-2">
                  <span className={`material-symbols-outlined text-[20px] ${
                    isOtherJobRunning ? 'text-primary animate-spin' : activeOtherJob.status === 'COMPLETED' ? 'text-tertiary' : 'text-error'
                  }`}>
                    {isOtherJobRunning ? 'sync' : activeOtherJob.status === 'COMPLETED' ? 'check_circle' : 'error'}
                  </span>
                  <span className="font-headline-sm text-sm font-bold text-primary">
                    Import Job: {activeOtherJob.jobId}
                  </span>
                  <span className={`font-code-sm text-[10px] px-2 py-0.5 rounded-full font-bold ${
                    activeOtherJob.status === 'RUNNING'
                      ? 'bg-primary-container/30 text-primary'
                      : activeOtherJob.status === 'COMPLETED'
                      ? 'bg-tertiary-container/30 text-tertiary'
                      : activeOtherJob.status === 'COMPLETED_WITH_WARNINGS'
                      ? 'bg-amber-500/20 text-amber-400'
                      : 'bg-error-container/30 text-error'
                  }`}>
                    {activeOtherJob.status}
                  </span>
                </div>

                {activeOtherJob.status === 'COMPLETED' && (
                  <button
                    onClick={() => {
                      const target = activeOtherJob.importedItems[0] || activeProvider;
                      const prompt = activeProvider === 'confluence'
                        ? `What pages and architecture documentation are in Confluence space ${target}?`
                        : `What commits and pull/merge requests were imported from ${activeProvider.toUpperCase()} for ${target}?`;
                      onNavigateToAsk(prompt);
                    }}
                    className="inline-flex items-center gap-1.5 px-3 py-1 rounded-lg bg-tertiary-container/30 hover:bg-tertiary-container/50 border border-tertiary/40 text-xs font-label-md text-tertiary cursor-pointer transition-all"
                  >
                    <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                    Explore in Ask &amp; Investigate
                  </button>
                )}
              </div>

              {/* Progress Bar */}
              <div className="space-y-1">
                <div className="flex items-center justify-between text-xs font-code-sm">
                  <span className="text-on-surface-variant font-medium">
                    Phase: <strong className="text-primary">{activeOtherJob.currentPhase}</strong>
                  </span>
                  <span className="text-secondary font-semibold">
                    {otherProgressPercent}%
                  </span>
                </div>
                <div className="w-full h-2 rounded-full bg-surface-container overflow-hidden">
                  <div
                    className={`h-full transition-all duration-300 ${
                      activeOtherJob.status === 'FAILED' ? 'bg-error' : 'bg-primary'
                    }`}
                    style={{ width: `${otherProgressPercent}%` }}
                  />
                </div>
              </div>

              {/* Metric Badges */}
              <div className="grid grid-cols-2 sm:grid-cols-4 gap-2 pt-1 font-code-sm text-xs">
                <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                  <span className="text-[10px] text-outline uppercase font-semibold">Items Processed</span>
                  <span className="font-bold text-primary mt-0.5">
                    {activeOtherJob.itemsProcessed} / {activeOtherJob.itemsTotal}
                  </span>
                </div>

                <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                  <span className="text-[10px] text-outline uppercase font-semibold">
                    {activeProvider === 'confluence' ? 'Documents' : 'Commits Imported'}
                  </span>
                  <span className="font-bold text-secondary mt-0.5">
                    {activeOtherJob.commitsImported}
                  </span>
                </div>

                <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                  <span className="text-[10px] text-outline uppercase font-semibold">
                    {activeProvider === 'confluence' ? 'Pages' : 'PRs / MRs Imported'}
                  </span>
                  <span className="font-bold text-tertiary mt-0.5">
                    {activeOtherJob.pullRequestsImported}
                  </span>
                </div>

                <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                  <span className="text-[10px] text-outline uppercase font-semibold">Vector Chunks</span>
                  <span className="font-bold text-primary mt-0.5">
                    {activeOtherJob.chunksCreated}
                  </span>
                </div>
              </div>

              {/* Log Accordion */}
              {activeOtherJob.logs && activeOtherJob.logs.length > 0 && (
                <div className="pt-2 border-t border-outline-variant/20">
                  <button
                    type="button"
                    onClick={() => setShowOtherLogs(p => !p)}
                    className="flex items-center justify-between w-full text-left text-xs font-code-sm text-outline hover:text-primary transition-colors cursor-pointer"
                  >
                    <span className="flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[16px]">terminal</span>
                      Import Logs ({activeOtherJob.logs.length} entries)
                    </span>
                    <span className="material-symbols-outlined text-[16px]">
                      {showOtherLogs ? 'expand_less' : 'expand_more'}
                    </span>
                  </button>
                  {showOtherLogs && (
                    <div className="mt-2 p-3 rounded-lg bg-surface-container-lowest max-h-48 overflow-y-auto font-mono text-[11px] text-on-surface-variant space-y-1">
                      {activeOtherJob.logs.map((log, lIdx) => (
                        <div key={lIdx} className="leading-tight">{log}</div>
                      ))}
                    </div>
                  )}
                </div>
              )}
            </div>
          )}

          {/* Discovered Items & Selection Panel (when connected) */}
          {otherStatuses[activeProvider]?.connected && (
            <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-4">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="space-y-0.5">
                  <div className="flex items-center gap-2">
                    <span className="material-symbols-outlined text-secondary text-[20px]">
                      {activeProvider === 'confluence' ? 'folder_special' : 'code_blocks'}
                    </span>
                    <h3 className="font-headline-sm text-sm font-bold text-primary capitalize">
                      Accessible {activeProvider} {activeProvider === 'confluence' ? 'Spaces' : 'Repositories & Projects'}
                    </h3>
                  </div>
                  <p className="font-body-sm text-xs text-on-surface-variant">
                    Select items to import metadata, {activeProvider === 'confluence' ? 'pages, and documentation' : 'commits, and pull/merge requests'} directly into FalkorDB.
                  </p>
                </div>

                <div className="flex items-center gap-2">
                  <button
                    onClick={() => loadSourceItemsForProvider(activeProvider)}
                    disabled={sourceItemsLoading}
                    className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 text-xs font-label-md text-on-surface-variant hover:text-primary transition-all cursor-pointer"
                  >
                    <span className={`material-symbols-outlined text-[16px] ${sourceItemsLoading ? 'animate-spin' : ''}`}>
                      sync
                    </span>
                    <span>Refresh</span>
                  </button>

                  <button
                    onClick={handleStartOtherImport}
                    disabled={isOtherJobRunning || selectedSourceItems.size === 0}
                    className="inline-flex items-center gap-2 px-4 py-1.5 rounded-lg bg-primary hover:bg-primary/90 text-on-primary font-label-md text-xs font-semibold shadow-sm transition-all active:scale-95 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                  >
                    <span className={`material-symbols-outlined text-[16px] ${isStartingOtherImport ? 'animate-spin' : ''}`}>
                      {isStartingOtherImport ? 'sync' : 'download'}
                    </span>
                    <span>
                      {isStartingOtherImport ? 'Queueing...' : `Import Selected (${selectedSourceItems.size})`}
                    </span>
                  </button>
                </div>
              </div>

              {startOtherImportError && (
                <div className="p-3 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm">
                  {startOtherImportError}
                </div>
              )}

              {/* Search & Select-all Toolbar */}
              <div className="flex flex-col sm:flex-row items-stretch sm:items-center justify-between gap-2 pt-2 border-t border-outline-variant/20">
                <div className="relative flex-1 max-w-md">
                  <span className="material-symbols-outlined absolute left-2.5 top-2 text-[16px] text-outline">
                    search
                  </span>
                  <input
                    type="text"
                    value={sourceSearchFilter}
                    onChange={e => setSourceSearchFilter(e.target.value)}
                    placeholder={`Filter ${activeProvider} items by name, path, or description...`}
                    className="w-full pl-8 pr-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs text-primary placeholder:text-outline focus:outline-none focus:border-primary transition-colors"
                  />
                </div>

                <div className="flex items-center gap-2 text-xs">
                  <button
                    onClick={handleSelectAllSourceItems}
                    className="text-secondary hover:underline cursor-pointer font-medium"
                  >
                    {selectedSourceItems.size === filteredSourceItems.length && filteredSourceItems.length > 0
                      ? 'Deselect All'
                      : 'Select All'}
                  </button>
                  <span className="text-outline">·</span>
                  <span className="text-outline">
                    {selectedSourceItems.size} of {filteredSourceItems.length} selected
                  </span>
                </div>
              </div>

              {/* Items Grid */}
              {sourceItemsLoading ? (
                <div className="p-8 text-center text-xs text-on-surface-variant flex flex-col items-center gap-2">
                  <span className="material-symbols-outlined text-[24px] animate-spin text-secondary">
                    progress_activity
                  </span>
                  Discovering {activeProvider} items...
                </div>
              ) : sourceItemsError ? (
                <div className="p-4 rounded-lg bg-error-container/20 border border-error-container/40 text-xs text-error font-body-sm">
                  {sourceItemsError}
                </div>
              ) : filteredSourceItems.length === 0 ? (
                <div className="p-6 text-center text-xs text-outline font-body-sm rounded-lg bg-surface-container">
                  No {activeProvider} items found matching your filter.
                </div>
              ) : (
                <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-3">
                  {filteredSourceItems.map(item => {
                    const itemKey = item.id || item.fullName;
                    const isSelected = selectedSourceItems.has(itemKey);
                    return (
                      <div
                        key={itemKey}
                        onClick={() => !isOtherJobRunning && toggleSourceItemSelection(itemKey)}
                        className={`p-3 rounded-xl border text-xs flex flex-col justify-between transition-all cursor-pointer ${
                          isSelected
                            ? 'bg-primary-container/15 border-primary shadow-sm'
                            : 'bg-surface-container hover:bg-surface-container-high border-outline-variant/20'
                        }`}
                      >
                        <div className="space-y-1.5">
                          <div className="flex items-start justify-between gap-2">
                            <div className="flex items-center gap-2 min-w-0">
                              <input
                                type="checkbox"
                                checked={isSelected}
                                onChange={() => {}}
                                disabled={isOtherJobRunning}
                                className="rounded text-primary focus:ring-0 cursor-pointer"
                              />
                              <span className="font-semibold text-primary truncate" title={item.fullName || item.name}>
                                {item.fullName || item.name}
                              </span>
                            </div>
                            <span className="font-code-sm text-[9px] px-1.5 py-0.2 rounded font-medium bg-surface-container-highest text-outline flex-shrink-0">
                              {item.type || (item.isPrivate ? 'private' : 'public')}
                            </span>
                          </div>
                          <p className="font-body-sm text-[11px] text-on-surface-variant line-clamp-2">
                            {item.description || 'No description provided.'}
                          </p>
                        </div>

                        {item.defaultBranch && (
                          <div className="text-[10px] font-code-sm text-outline pt-2 mt-2 border-t border-outline-variant/15 flex items-center justify-between">
                            <span>branch: {item.defaultBranch}</span>
                            {item.webUrl && (
                              <a
                                href={item.webUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                onClick={e => e.stopPropagation()}
                                className="hover:text-primary transition-colors inline-flex items-center gap-0.5"
                              >
                                <span>open</span>
                                <span className="material-symbols-outlined text-[10px]">open_in_new</span>
                              </a>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </div>
          )}

          {/* Recent Import Jobs History for other provider */}
          {recentOtherJobs.length > 0 && (
            <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 space-y-3">
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="material-symbols-outlined text-secondary text-[20px]">
                    history
                  </span>
                  <span className="font-headline-sm text-sm font-bold text-primary capitalize">
                    Recent {activeProvider} Ingestion Jobs
                  </span>
                </div>
                <button
                  onClick={() => loadRecentOtherJobsForProvider(activeProvider)}
                  className="text-xs text-secondary hover:underline cursor-pointer"
                >
                  Refresh
                </button>
              </div>

              <div className="divide-y divide-outline-variant/20 font-code-sm text-xs">
                {recentOtherJobs.map(job => (
                  <div
                    key={job.jobId}
                    onClick={() => {
                      setActiveOtherJobId(job.jobId);
                      setActiveOtherJob(job);
                    }}
                    className="py-2.5 flex items-center justify-between gap-3 hover:bg-surface-container/50 px-2 rounded cursor-pointer transition-colors"
                  >
                    <div className="flex items-center gap-2 min-w-0">
                      <span className={`w-2 h-2 rounded-full flex-shrink-0 ${
                        job.status === 'COMPLETED'
                          ? 'bg-tertiary'
                          : job.status === 'RUNNING'
                          ? 'bg-primary animate-pulse'
                          : job.status === 'COMPLETED_WITH_WARNINGS'
                          ? 'bg-amber-400'
                          : 'bg-error'
                      }`} />
                      <span className="font-semibold text-primary">{job.jobId}</span>
                      <span className="text-[11px] text-on-surface-variant truncate hidden sm:inline">
                        {job.importedItems.join(', ') || `${job.itemsTotal} item(s)`}
                      </span>
                    </div>

                    <div className="flex items-center gap-3 text-[11px] text-outline flex-shrink-0">
                      <span>{job.itemsProcessed}/{job.itemsTotal} items</span>
                      <span>•</span>
                      <span>{job.chunksCreated} chunks</span>
                      <span className={`font-semibold uppercase text-[10px] px-1.5 py-0.2 rounded ${
                        job.status === 'COMPLETED'
                          ? 'bg-tertiary-container/30 text-tertiary'
                          : job.status === 'RUNNING'
                          ? 'bg-primary-container/30 text-primary'
                          : 'bg-error-container/30 text-error'
                      }`}>
                        {job.status}
                      </span>
                    </div>
                  </div>
                ))}
              </div>
            </div>
          )}
        </div>
      )}
    </div>
  );
};
