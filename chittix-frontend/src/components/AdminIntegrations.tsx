import React, { useState, useEffect, useCallback } from 'react';
import type { HealthResponse, ProviderStatusResponse, ProviderTestResult } from '../services/api';
import type { GitHubStatusResponse } from '../types/github';
import { getGitHubStatus, testProviderConnection, configureProvider } from '../services/api';
import type { ScreenId } from '../types/navigation';

interface AdminIntegrationsProps {
  health: HealthResponse | null;
  healthLoading: boolean;
  onRefreshHealth: () => void;
  providerStatus: ProviderStatusResponse | null;
  providerStatusLoading: boolean;
  onRefreshProviderStatus: () => void;
  onNavigateToScreen?: (screen: ScreenId) => void;
  onNavigateToAsk?: (query?: string) => void;
}

const PROVIDER_METADATA: Record<string, { model: string; baseUrl: string; requiresKey: boolean; label: string; description: string }> = {
  ollama: {
    model: 'qwen2.5:1.5b',
    baseUrl: 'http://localhost:11434',
    requiresKey: false,
    label: 'Ollama',
    description: 'Local inference via Ollama runtime',
  },
  'openai-compatible': {
    model: 'gpt-4o-mini',
    baseUrl: 'http://localhost:8000/v1',
    requiresKey: false,
    label: 'OpenAI-Compatible',
    description: 'vLLM, LocalAI, LM Studio, Groq, or gateway',
  },
  openai: {
    model: 'gpt-4o-mini',
    baseUrl: 'https://api.openai.com/v1',
    requiresKey: true,
    label: 'OpenAI',
    description: 'Official OpenAI GPT-4o-mini / GPT-4o models',
  },
  gemini: {
    model: 'gemini-1.5-flash',
    baseUrl: 'https://generativelanguage.googleapis.com',
    requiresKey: true,
    label: 'Google Gemini',
    description: 'Google Generative Language Gemini-1.5 models',
  },
  anthropic: {
    model: 'claude-3-5-haiku-20241022',
    baseUrl: 'https://api.anthropic.com',
    requiresKey: true,
    label: 'Anthropic',
    description: 'Anthropic Claude-3.5 Haiku / Sonnet models',
  },
};

export const AdminIntegrations: React.FC<AdminIntegrationsProps> = ({
  health,
  healthLoading,
  onRefreshHealth,
  providerStatus,
  providerStatusLoading,
  onRefreshProviderStatus,
  onNavigateToScreen,
  onNavigateToAsk,
}) => {
  const [gitHubStatus, setGitHubStatus] = useState<GitHubStatusResponse | null>(null);
  const [gitHubLoading, setGitHubLoading] = useState<boolean>(true);
  const [gitHubError, setGitHubError] = useState<string | null>(null);

  type ProviderDraft = { model: string; baseUrl: string; apiKey: string };

  const buildDefaultDrafts = (): Record<string, ProviderDraft> => {
    const next: Record<string, ProviderDraft> = {};
    Object.entries(PROVIDER_METADATA).forEach(([id, meta]) => {
      next[id] = { model: meta.model, baseUrl: meta.baseUrl, apiKey: '' };
    });
    return next;
  };

  const [formProvider, setFormProvider] = useState<string>('ollama');
  const [drafts, setDrafts] = useState<Record<string, ProviderDraft>>(buildDefaultDrafts);
  const [dirtyByProvider, setDirtyByProvider] = useState<Record<string, boolean>>({});
  const [showApiKey, setShowApiKey] = useState<boolean>(false);
  const [endpointError, setEndpointError] = useState<string | null>(null);
  const [statusHydrated, setStatusHydrated] = useState<boolean>(false);

  const [isTesting, setIsTesting] = useState<boolean>(false);
  const [testResult, setTestResult] = useState<ProviderTestResult | null>(null);
  const [testError, setTestError] = useState<string | null>(null);
  const [isSaving, setIsSaving] = useState<boolean>(false);
  const [saveSuccess, setSaveSuccess] = useState<string | null>(null);
  const [saveError, setSaveError] = useState<string | null>(null);

  const currentDraft = drafts[formProvider] || {
    model: PROVIDER_METADATA.ollama.model,
    baseUrl: PROVIDER_METADATA.ollama.baseUrl,
    apiKey: '',
  };
  const formModel = currentDraft.model;
  const formBaseUrl = currentDraft.baseUrl;
  const formApiKey = currentDraft.apiKey;

  const markDirty = (providerId: string) => {
    setDirtyByProvider(prev => (prev[providerId] ? prev : { ...prev, [providerId]: true }));
  };

  const updateCurrentDraft = (patch: Partial<ProviderDraft>) => {
    markDirty(formProvider);
    setDrafts(prev => ({
      ...prev,
      [formProvider]: { ...(prev[formProvider] || buildDefaultDrafts()[formProvider]), ...patch },
    }));
  };

  const validateEndpoint = (value: string): string | null => {
    const trimmed = value.trim();
    if (!trimmed) {
      return 'Endpoint URL is required.';
    }
    try {
      const parsed = new URL(trimmed);
      if (parsed.protocol !== 'http:' && parsed.protocol !== 'https:') {
        return 'Endpoint must use http or https.';
      }
      return null;
    } catch {
      return 'Enter a valid endpoint URL.';
    }
  };

  const isFalkorDbUp = health?.falkordb === 'UP';

  const loadGitHubStatus = useCallback(async () => {
    setGitHubLoading(true);
    setGitHubError(null);
    try {
      const res = await getGitHubStatus();
      setGitHubStatus(res);
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Failed to reach GitHub status endpoint';
      setGitHubError(msg);
      setGitHubStatus(null);
    } finally {
      setGitHubLoading(false);
    }
  }, []);

  useEffect(() => {
    loadGitHubStatus();
  }, [loadGitHubStatus]);

  // Hydrate each provider's stored model/endpoint from backend status. Never overwrite dirty drafts or API keys.
  useEffect(() => {
    if (!providerStatus?.selectedProvider) {
      return;
    }
    const activeId = providerStatus.selectedProvider;
    setDrafts(prev => {
      const next: Record<string, ProviderDraft> = { ...prev };
      const infos = providerStatus.availableProviders || [];
      infos.forEach(info => {
        if (!info?.provider || dirtyByProvider[info.provider]) {
          return;
        }
        const meta = PROVIDER_METADATA[info.provider];
        if (!meta) {
          return;
        }
        const isActive = info.provider === activeId;
        next[info.provider] = {
          model: info.model || (isActive ? providerStatus.configuredModel : undefined) || meta.model,
          baseUrl: info.baseUrl || (isActive ? providerStatus.baseUrl : undefined) || meta.baseUrl,
          apiKey: prev[info.provider]?.apiKey || '',
        };
      });
      if (!infos.some(info => info.provider === activeId) && !dirtyByProvider[activeId]) {
        const meta = PROVIDER_METADATA[activeId] || PROVIDER_METADATA.ollama;
        next[activeId] = {
          model: providerStatus.configuredModel || meta.model,
          baseUrl: providerStatus.baseUrl || meta.baseUrl,
          apiKey: prev[activeId]?.apiKey || '',
        };
      }
      return next;
    });
    if (!statusHydrated) {
      setFormProvider(activeId);
      setStatusHydrated(true);
    }
  }, [providerStatus, dirtyByProvider, statusHydrated]);

  const handleSelectProvider = (provId: string) => {
    if (provId === formProvider) {
      return;
    }
    setFormProvider(provId);
    setShowApiKey(false);
    setEndpointError(null);
    setTestResult(null);
    setTestError(null);
    setSaveSuccess(null);
    setSaveError(null);
  };

  const handleTestConnection = async () => {
    const urlError = validateEndpoint(formBaseUrl);
    setEndpointError(urlError);
    setIsTesting(true);
    setTestResult(null);
    setTestError(null);
    setSaveSuccess(null);
    setSaveError(null);
    if (urlError) {
      setIsTesting(false);
      setTestError(urlError);
      return;
    }
    try {
      const res = await testProviderConnection({
        provider: formProvider,
        model: formModel.trim() || undefined,
        baseUrl: formBaseUrl.trim(),
        apiKey: formApiKey.trim() || undefined,
      });
      setTestResult(res);
      if (!res.success) {
        setTestError(res.message);
      }
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Connection test failed';
      setTestError(msg);
    } finally {
      setIsTesting(false);
    }
  };

  const handleSaveAndActivate = async () => {
    const urlError = validateEndpoint(formBaseUrl);
    setEndpointError(urlError);
    setIsSaving(true);
    setSaveSuccess(null);
    setSaveError(null);
    if (urlError) {
      setIsSaving(false);
      setSaveError(urlError);
      return;
    }
    try {
      const updatedStatus = await configureProvider({
        provider: formProvider,
        model: formModel.trim() || undefined,
        baseUrl: formBaseUrl.trim(),
        apiKey: formApiKey.trim() || undefined,
      });
      setDrafts(prev => ({
        ...prev,
        [formProvider]: {
          model: updatedStatus.configuredModel || formModel,
          baseUrl: updatedStatus.baseUrl || formBaseUrl,
          apiKey: '',
        },
      }));
      setDirtyByProvider(prev => ({ ...prev, [formProvider]: false }));
      setSaveSuccess(
        `Saved and activated ${updatedStatus.selectedProvider.toUpperCase()} (${updatedStatus.configuredModel}). Connection test succeeded as part of activation.`
      );
      onRefreshProviderStatus();
    } catch (err: unknown) {
      const msg = err instanceof Error ? err.message : 'Configuration activation failed';
      setSaveError(msg);
    } finally {
      setIsSaving(false);
    }
  };

  const handleRefreshAll = () => {
    onRefreshHealth();
    onRefreshProviderStatus();
    loadGitHubStatus();
  };

  const currentMeta = PROVIDER_METADATA[formProvider] || PROVIDER_METADATA.ollama;
  const showApiKeyField = currentMeta.requiresKey || formProvider === 'openai-compatible';
  const usingDefaultEndpoint =
    formBaseUrl.trim().replace(/\/+$/, '') === currentMeta.baseUrl.replace(/\/+$/, '');
  const selectedProviderInfo = providerStatus?.availableProviders?.find(
    info => info.provider === formProvider
  );
  const credentialOnFile = Boolean(
    selectedProviderInfo?.apiKeyConfigured ?? (currentMeta.requiresKey && selectedProviderInfo?.configured)
  );

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      {/* View Header */}
      <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-4 pb-2 border-b border-outline-variant/30">
        <div>
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[26px]">
              settings_ethernet
            </span>
            <h1 className="font-headline-sm text-2xl font-bold tracking-tight text-primary">
              Integrations &amp; Admin
            </h1>
            <span className="font-code-sm text-[10px] px-2 py-0.5 rounded-full bg-secondary-container/30 text-secondary border border-secondary/30 font-semibold uppercase">
              Management
            </span>
          </div>
          <p className="font-body-sm text-xs text-on-surface-variant mt-1">
            System connectivity, model configuration, and external integration health.
          </p>
        </div>

        <button
          onClick={handleRefreshAll}
          title="Refresh all integration statuses"
          className="inline-flex items-center gap-2 px-3.5 py-2 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 text-xs font-label-md text-on-surface-variant hover:text-primary transition-all active:scale-95 cursor-pointer self-start sm:self-auto shadow-sm"
        >
          <span className={`material-symbols-outlined text-[16px] ${healthLoading || providerStatusLoading || gitHubLoading ? 'animate-spin' : ''}`}>
            sync
          </span>
          <span>Refresh All</span>
        </button>
      </div>

      {/* Primary Integration Cards: FalkorDB & GitHub */}
      <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
        {/* FalkorDB Card */}
        <section className="bg-surface-container rounded-2xl p-5 border border-outline-variant/30 shadow-sm flex flex-col justify-between space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="material-symbols-outlined text-secondary text-[22px]">database</span>
              <div>
                <h2 className="font-headline-sm text-sm text-primary font-bold">
                  FalkorDB Graph Store
                </h2>
                <span className="font-body-sm text-[11px] text-on-surface-variant">
                  Knowledge graph database
                </span>
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <span
                className={`inline-flex items-center gap-1.5 font-code-sm text-[11px] px-2.5 py-0.5 rounded-full font-semibold border ${
                  healthLoading
                    ? 'bg-amber-400/20 text-amber-400 border-amber-400/30'
                    : isFalkorDbUp
                    ? 'bg-tertiary-container/30 text-tertiary border-tertiary/30'
                    : 'bg-error-container/30 text-error border-error/30'
                }`}
              >
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    healthLoading
                      ? 'bg-amber-400 animate-pulse'
                      : isFalkorDbUp
                      ? 'bg-tertiary'
                      : 'bg-error'
                  }`}
                />
                {healthLoading ? 'CHECKING...' : isFalkorDbUp ? 'CONNECTED' : 'OFFLINE'}
              </span>

              <button
                onClick={onRefreshHealth}
                disabled={healthLoading}
                className="p-1 rounded-md text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
                title="Re-check FalkorDB"
              >
                <span className="material-symbols-outlined text-[16px]">refresh</span>
              </button>
            </div>
          </div>

          <div className="grid grid-cols-2 gap-2 text-xs">
            <div className="p-2.5 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col">
              <span className="font-code-sm text-[9px] text-outline uppercase font-semibold">Graph</span>
              <span className="font-bold text-secondary font-mono text-xs">{health?.graph || 'company_brain'}</span>
            </div>
            <div className="p-2.5 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col">
              <span className="font-code-sm text-[9px] text-outline uppercase font-semibold">Host / Port</span>
              <span className="font-bold text-primary text-xs">{health?.application ? 'localhost:6379' : 'Disconnected'}</span>
            </div>
          </div>

          <div className="text-[11px] text-on-surface-variant flex items-center gap-1.5 pt-1">
            <span className={`material-symbols-outlined text-[14px] ${isFalkorDbUp ? 'text-tertiary' : 'text-error'}`}>
              {isFalkorDbUp ? 'check_circle' : 'error'}
            </span>
            <span>{health?.message || 'Connecting to graph...'}</span>
          </div>
        </section>

        {/* GitHub Card */}
        <section className="bg-surface-container rounded-2xl p-5 border border-outline-variant/30 shadow-sm flex flex-col justify-between space-y-3">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-2.5">
              <span className="material-symbols-outlined text-secondary text-[22px]">deployed_code</span>
              <div>
                <h2 className="font-headline-sm text-sm text-primary font-bold">
                  GitHub Integration
                </h2>
                <span className="font-body-sm text-[11px] text-on-surface-variant">
                  Repository &amp; commit provenance
                </span>
              </div>
            </div>

            <div className="flex items-center gap-1.5">
              <span
                className={`inline-flex items-center gap-1.5 font-code-sm text-[11px] px-2.5 py-0.5 rounded-full font-semibold border ${
                  gitHubLoading
                    ? 'bg-amber-400/20 text-amber-400 border-amber-400/30'
                    : gitHubStatus?.configured
                    ? 'bg-tertiary-container/30 text-tertiary border-tertiary/30'
                    : 'bg-amber-500/20 text-amber-400 border-amber-500/30'
                }`}
              >
                <span
                  className={`w-1.5 h-1.5 rounded-full ${
                    gitHubLoading
                      ? 'bg-amber-400 animate-pulse'
                      : gitHubStatus?.configured
                      ? 'bg-tertiary'
                      : 'bg-amber-400'
                  }`}
                />
                {gitHubLoading
                  ? 'CHECKING...'
                  : gitHubStatus?.configured
                  ? 'CONNECTED'
                  : 'TOKEN MISSING'}
              </span>

              <button
                onClick={loadGitHubStatus}
                disabled={gitHubLoading}
                className="p-1 rounded-md text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
                title="Re-check GitHub status"
              >
                <span className="material-symbols-outlined text-[16px]">refresh</span>
              </button>
            </div>
          </div>

          {gitHubStatus?.configured ? (
            <div className="space-y-2">
              <div className="flex items-center justify-between p-2.5 rounded-xl bg-surface-container-low border border-outline-variant/20 text-xs">
                <div className="flex items-center gap-2">
                  {gitHubStatus.avatarUrl ? (
                    <img src={gitHubStatus.avatarUrl} alt="" className="w-6 h-6 rounded-full border border-outline-variant/30" />
                  ) : (
                    <span className="material-symbols-outlined text-[16px]">person</span>
                  )}
                  <span className="font-semibold text-primary">@{gitHubStatus.username}</span>
                </div>
                <span className="font-code-sm text-[11px] text-outline">
                  Rate Limit: {gitHubStatus.rateLimitRemaining.toLocaleString()} / {gitHubStatus.rateLimitLimit.toLocaleString()}
                </span>
              </div>

              <button
                type="button"
                onClick={() => onNavigateToScreen?.('sources')}
                className="w-full py-1.5 px-3 rounded-lg bg-surface-container-high hover:bg-surface-bright text-xs font-semibold text-primary border border-outline-variant/30 flex items-center justify-center gap-1.5 cursor-pointer transition-colors"
              >
                <span>Manage Repository Sources</span>
                <span className="material-symbols-outlined text-[14px]">arrow_forward</span>
              </button>
            </div>
          ) : (
            <div className="p-2.5 rounded-xl bg-surface-container-low border border-amber-500/20 text-[11px] text-on-surface-variant space-y-1">
              <span className="font-semibold text-amber-400">
                {gitHubError ? 'Connection Issue' : 'Token not configured'}
              </span>
              <p>
                {gitHubError || (
                  <>GitHub repository access is not configured. Check your connection in GitHub Sources.</>
                )}
              </p>
            </div>
          )}
        </section>
      </div>

      {/* AI Models Configuration & Testing Card */}
      <section className="bg-surface-container rounded-2xl p-5 md:p-6 border border-outline-variant/30 shadow-sm space-y-4">
        <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-outline-variant/20">
          <div className="flex items-center gap-2.5">
            <span className="material-symbols-outlined text-secondary text-[22px]">psychology</span>
            <div>
              <h2 className="font-headline-sm text-base text-primary font-bold">
                AI Models
              </h2>
              <span className="font-body-sm text-xs text-on-surface-variant">
                Select and configure the language model for evidence-grounded analysis
              </span>
            </div>
          </div>

          <div className="flex items-center gap-2">
            <span
              className={`inline-flex items-center gap-1.5 font-code-sm text-xs px-2.5 py-0.5 rounded-full font-semibold border ${
                providerStatusLoading
                  ? 'bg-amber-400/20 text-amber-400 border-amber-400/30'
                  : providerStatus?.configured
                  ? 'bg-tertiary-container/30 text-tertiary border-tertiary/30'
                  : 'bg-amber-500/20 text-amber-400 border-amber-500/30'
              }`}
            >
              <span
                className={`w-2 h-2 rounded-full ${
                  providerStatusLoading
                    ? 'bg-amber-400 animate-pulse'
                    : providerStatus?.configured
                    ? 'bg-tertiary'
                    : 'bg-amber-400'
                }`}
              />
              {providerStatusLoading
                ? 'CHECKING...'
                : providerStatus?.configured
                ? `ACTIVE · ${providerStatus.selectedProvider?.toUpperCase()}`
                : 'NOT CONFIGURED'}
            </span>

            <button
              onClick={onRefreshProviderStatus}
              disabled={providerStatusLoading}
              className="p-1 rounded-md text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
              title="Refresh AI model status"
            >
              <span className="material-symbols-outlined text-[18px]">refresh</span>
            </button>
          </div>
        </div>

        {/* Current Active Provider Summary */}
        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[10px] text-outline uppercase font-semibold">Active Provider</span>
            <span className="font-headline-sm text-sm font-bold text-primary capitalize">{providerStatus?.selectedProvider || 'ollama'}</span>
          </div>

          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[10px] text-outline uppercase font-semibold">Active Model</span>
            <span className="font-headline-sm text-sm font-bold text-secondary font-mono truncate" title={providerStatus?.configuredModel}>
              {providerStatus?.configuredModel || 'qwen2.5:1.5b'}
            </span>
          </div>

          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[10px] text-outline uppercase font-semibold">Endpoint</span>
            <span className="font-headline-sm text-xs font-bold text-primary font-mono truncate" title={providerStatus?.baseUrl}>
              {providerStatus?.baseUrl || 'http://localhost:11434'}
            </span>
          </div>
        </div>

        {/* Provider Selector Buttons */}
        <div className="space-y-2 pt-1">
          <label className="font-label-md text-xs font-semibold text-primary block">
            Select Provider
          </label>
          <div className="grid grid-cols-2 sm:grid-cols-3 md:grid-cols-5 gap-2 text-xs">
            {Object.entries(PROVIDER_METADATA).map(([provId, meta]) => {
              const isSelected = formProvider === provId;
              const isActive = providerStatus?.selectedProvider === provId;

              return (
                <button
                  key={provId}
                  type="button"
                  onClick={() => handleSelectProvider(provId)}
                  className={`p-2.5 rounded-xl border text-left flex flex-col justify-between gap-1 transition-all cursor-pointer ${
                    isSelected
                      ? 'bg-primary-container/20 border-primary shadow-xs ring-1 ring-primary/40'
                      : 'bg-surface-container-low hover:bg-surface-container-high border-outline-variant/20'
                  }`}
                >
                  <div className="flex items-center justify-between w-full">
                    <span className="font-semibold text-xs text-primary">{meta.label}</span>
                    {isActive && (
                      <span className="font-code-sm text-[8px] px-1 py-0.2 rounded bg-tertiary-container/40 text-tertiary font-bold uppercase">
                        ACTIVE
                      </span>
                    )}
                  </div>
                  <span className="text-[10px] text-on-surface-variant truncate">{meta.description}</span>
                </button>
              );
            })}
          </div>
        </div>

        {/* Configuration Fields for Selected Provider */}
        <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 space-y-3.5">
          <div className="grid grid-cols-1 md:grid-cols-2 gap-3 text-xs">
            {/* Model Identifier */}
            <div className="space-y-1">
              <label htmlFor="provider-model" className="font-label-md text-xs font-semibold text-primary flex items-center justify-between">
                <span>Model Identifier</span>
                <span className="font-code-sm text-[10px] text-outline">e.g. {currentMeta.model}</span>
              </label>
              <input
                id="provider-model"
                type="text"
                value={formModel}
                onChange={e => updateCurrentDraft({ model: e.target.value })}
                placeholder="Model name..."
                autoComplete="off"
                className="w-full px-3 py-2 rounded-lg bg-surface-container border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:ring-1 focus:ring-primary"
              />
            </div>

            <div className="space-y-1">
              <label htmlFor="provider-endpoint" className="font-label-md text-xs font-semibold text-primary flex items-center justify-between gap-2">
                <span>Standard Endpoint</span>
                <span className={`font-code-sm text-[10px] ${usingDefaultEndpoint ? 'text-outline' : 'text-secondary'}`}>
                  {usingDefaultEndpoint ? 'Using provider default' : 'Custom endpoint'}
                </span>
              </label>
              <input
                id="provider-endpoint"
                type="url"
                value={formBaseUrl}
                onChange={e => {
                  const next = e.target.value;
                  updateCurrentDraft({ baseUrl: next });
                  setEndpointError(next.trim() ? validateEndpoint(next) : 'Endpoint URL is required.');
                }}
                placeholder={currentMeta.baseUrl}
                autoComplete="off"
                aria-invalid={Boolean(endpointError)}
                className={`w-full px-3 py-2 rounded-lg bg-surface-container border text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:ring-1 focus:ring-primary ${
                  endpointError ? 'border-error/50' : 'border-outline-variant/30'
                }`}
              />
              <div className="flex items-start justify-between gap-2">
                <p className="font-code-sm text-[10px] text-outline">
                  Default: {currentMeta.baseUrl}
                </p>
                {!usingDefaultEndpoint && (
                  <button
                    type="button"
                    onClick={() => {
                      updateCurrentDraft({ baseUrl: currentMeta.baseUrl });
                      setEndpointError(null);
                    }}
                    className="font-code-sm text-[10px] text-secondary hover:text-primary cursor-pointer"
                  >
                    Restore default
                  </button>
                )}
              </div>
              {endpointError && (
                <p className="text-[11px] text-error" role="alert">{endpointError}</p>
              )}
            </div>
          </div>

          {/* API Key (Only when required or allowed) */}
          {showApiKeyField && (
            <div className="space-y-1 pt-1 text-xs">
              <label htmlFor="provider-api-key" className="font-label-md text-xs font-semibold text-primary flex items-center justify-between">
                <span className="flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[15px] text-secondary">key</span>
                  <span>API Key</span>
                </span>
                <span className="font-code-sm text-[10px] text-outline">
                  {currentMeta.requiresKey
                    ? credentialOnFile
                      ? 'Key on file · leave blank to keep it'
                      : 'No key stored for this provider'
                    : 'Optional for this provider'}
                </span>
              </label>
              <div className="relative flex items-center">
                <input
                  id="provider-api-key"
                  type={showApiKey ? 'text' : 'password'}
                  value={formApiKey}
                  onChange={e => updateCurrentDraft({ apiKey: e.target.value })}
                  placeholder={
                    currentMeta.requiresKey
                      ? credentialOnFile
                        ? 'Replacement key (blank keeps the stored key)'
                        : 'Enter API key'
                      : 'Optional gateway key — blank keeps any stored key'
                  }
                  autoComplete="off"
                  className="w-full pl-3 pr-10 py-2 rounded-lg bg-surface-container border border-outline-variant/30 text-xs font-mono text-primary placeholder:text-outline focus:outline-none focus:ring-1 focus:ring-primary"
                />
                <button
                  type="button"
                  onClick={() => setShowApiKey(!showApiKey)}
                  className="absolute right-2 text-outline hover:text-primary transition-colors p-1 cursor-pointer"
                  title={showApiKey ? 'Hide key' : 'Show key'}
                  aria-label={showApiKey ? 'Hide API key' : 'Show API key'}
                >
                  <span className="material-symbols-outlined text-[16px]">
                    {showApiKey ? 'visibility_off' : 'visibility'}
                  </span>
                </button>
              </div>
              <p className="font-code-sm text-[10px] text-outline">
                Typed keys stay in this page's memory until Save or Test. They are not written to browser storage.
                Clearing this field does not delete a stored backend key. Key clearing is not supported from this form.
              </p>
            </div>
          )}

          {/* Collapsible Advanced Technical Details */}
          <details className="pt-1 text-xs text-on-surface-variant group">
            <summary className="cursor-pointer font-semibold text-outline hover:text-primary transition-colors flex items-center gap-1">
              <span className="material-symbols-outlined text-[16px] group-open:rotate-90 transition-transform">
                chevron_right
              </span>
              <span>Advanced Provider Details &amp; Capabilities</span>
            </summary>
            <div className="pt-2 pl-5 space-y-2 text-[11px] text-outline">
              <div>
                <strong>Selected provider capabilities: </strong>
                {(selectedProviderInfo?.supportedCapabilities || providerStatus?.supportedCapabilities)?.join(', ') || 'Not reported'}
              </div>
              <p>
                Temperature, maximum output tokens, and request timeout are server properties
                (<code>chittix.llm.temperature</code>, <code>chittix.llm.max-tokens</code>, <code>chittix.llm.timeout-seconds</code>)
                and cannot be changed from this form.
              </p>
              <p>
                Test Connection checks the values currently in this form without activating them.
                Save &amp; Activate runs the same connection test, then applies the configuration only if that test succeeds.
                Runtime updates are in-memory for this backend process and are not written to disk.
              </p>
            </div>
          </details>

          {/* Two Primary Action Buttons */}
          <div className="flex flex-wrap items-center justify-between gap-3 pt-2 border-t border-outline-variant/20">
            <div className="flex items-center gap-2">
              <button
                type="button"
                onClick={handleTestConnection}
                disabled={isTesting || isSaving}
                className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-headline-sm text-xs font-semibold flex items-center gap-1.5 border border-outline-variant/30 active:scale-95 transition-all cursor-pointer shadow-sm disabled:opacity-50"
              >
                <span className={`material-symbols-outlined text-[16px] ${isTesting ? 'animate-spin' : ''}`}>
                  {isTesting ? 'sync' : 'network_check'}
                </span>
                <span>{isTesting ? 'Testing...' : 'Test Connection'}</span>
              </button>

              <button
                type="button"
                onClick={handleSaveAndActivate}
                disabled={isSaving || isTesting}
                className="px-4 py-2 rounded-lg bg-primary text-on-primary font-headline-sm text-xs font-bold flex items-center gap-1.5 active:scale-95 transition-all cursor-pointer shadow-sm disabled:opacity-50"
              >
                <span className={`material-symbols-outlined text-[16px] ${isSaving ? 'animate-spin' : ''}`}>
                  {isSaving ? 'sync' : 'check_circle'}
                </span>
                <span>{isSaving ? 'Activating...' : 'Save & Activate'}</span>
              </button>
            </div>

            <button
              type="button"
              onClick={() => onNavigateToAsk?.('Why PostgreSQL for Payments?')}
              className="text-xs text-secondary hover:text-primary transition-colors flex items-center gap-1 font-semibold cursor-pointer"
            >
              <span>Ask &amp; Investigate</span>
              <span className="material-symbols-outlined text-[15px]">arrow_forward</span>
            </button>
          </div>

          {/* Test Feedback Result */}
          {testResult && (
            <div
              className={`p-3 rounded-lg border flex flex-col sm:flex-row sm:items-center justify-between gap-2 text-xs transition-all ${
                testResult.success
                  ? 'bg-tertiary-container/20 border-tertiary/40 text-tertiary'
                  : 'bg-error-container/20 border-error/40 text-error'
              }`}
            >
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[18px]">
                  {testResult.success ? 'check_circle' : 'error'}
                </span>
                <span className="font-semibold text-primary">{testResult.message}</span>
              </div>
              {testResult.latencyMs !== null && testResult.latencyMs !== undefined && (
                <span className="font-code-sm text-[10px] px-2 py-0.5 rounded bg-surface-container font-mono text-outline self-start sm:self-auto">
                  {testResult.latencyMs}ms
                </span>
              )}
            </div>
          )}

          {testError && !testResult && (
            <div className="p-3 rounded-lg bg-error-container/20 border border-error/40 text-xs text-error flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">error</span>
              <span>{testError}</span>
            </div>
          )}

          {/* Save / Activation Notifications */}
          {saveSuccess && (
            <div className="p-3 rounded-lg bg-tertiary-container/30 border border-tertiary/40 text-xs text-tertiary flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">verified</span>
              <span className="font-semibold">{saveSuccess}</span>
            </div>
          )}

          {saveError && (
            <div className="p-3 rounded-lg bg-error-container/20 border border-error/40 text-xs text-error flex items-center gap-2">
              <span className="material-symbols-outlined text-[18px]">warning</span>
              <span>{saveError}</span>
            </div>
          )}
        </div>
      </section>

      {/* Local Vector Embeddings (ONNX) Architecture Card */}
      <section className="bg-surface-container rounded-2xl p-5 border border-outline-variant/30 shadow-sm space-y-3">
        <div className="flex items-center gap-2.5 pb-2 border-b border-outline-variant/20">
          <span className="material-symbols-outlined text-tertiary text-[20px]">category</span>
          <div>
            <h2 className="font-headline-sm text-sm text-primary font-bold">
              Vector Embeddings Architecture
            </h2>
            <span className="font-body-sm text-xs text-on-surface-variant">
              In-process quantized text embedding pipeline decoupled from generative LLMs
            </span>
          </div>
        </div>

        <div className="grid grid-cols-1 sm:grid-cols-3 gap-3 text-xs">
          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[9px] text-outline uppercase font-semibold">Embedding Model</span>
            <span className="font-headline-sm text-xs font-bold text-tertiary font-mono">all-MiniLM-L6-v2</span>
            <span className="text-on-surface-variant text-[10px]">In-process ONNX Quantized</span>
          </div>

          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[9px] text-outline uppercase font-semibold">Dimensions</span>
            <span className="font-headline-sm text-xs font-bold text-primary font-mono">384 Dimensions</span>
            <span className="text-on-surface-variant text-[10px]">Cosine Distance Metric</span>
          </div>

          <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-0.5">
            <span className="font-code-sm text-[9px] text-outline uppercase font-semibold">Index Target</span>
            <span className="font-code-sm text-xs font-semibold text-secondary font-mono truncate">:DocumentChunk(embedding)</span>
            <span className="text-on-surface-variant text-[10px]">FalkorDB Vector Index</span>
          </div>
        </div>
      </section>
    </div>
  );
};
