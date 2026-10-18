import React, { useState } from 'react';
import type {
  HealthResponse,
  PaymentsContextResponse,
  DecisionContext,
  ProviderStatusResponse,
} from '../services/api';
import type { ScreenId } from '../types/navigation';

interface CompanyPulseProps {
  health: HealthResponse | null;
  healthLoading: boolean;
  paymentsContext: PaymentsContextResponse | null;
  loadingContext: boolean;
  providerStatus?: ProviderStatusResponse | null;
  providerStatusLoading?: boolean;
  onRefreshData: () => void;
  onNavigateToAsk: (query?: string) => void;
  onNavigateToScreen?: (screen: ScreenId) => void;
}

export const CompanyPulse: React.FC<CompanyPulseProps> = ({
  health,
  healthLoading,
  paymentsContext,
  loadingContext,
  providerStatus,
  providerStatusLoading,
  onRefreshData,
  onNavigateToAsk,
  onNavigateToScreen,
}) => {
  const [searchQuery, setSearchQuery] = useState('');

  const isGraphOnline = health?.falkordb === 'UP';
  const contexts = paymentsContext?.contexts || [];
  const primaryContext: DecisionContext | undefined = contexts[0];

  const healthStatusText = healthLoading
    ? 'Checking...'
    : isGraphOnline
    ? 'Online'
    : 'Offline';

  const healthStatusColor = healthLoading
    ? 'bg-amber-400 animate-pulse'
    : isGraphOnline
    ? 'bg-tertiary animate-pulse'
    : 'bg-error';

  const handleQuerySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      onNavigateToAsk(searchQuery.trim());
    }
  };

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      <div className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl bg-surface-container-low border border-outline-variant/30 shadow-sm transition-colors">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className={`inline-block w-2.5 h-2.5 rounded-full flex-shrink-0 ${healthStatusColor}`} />
          <span className="font-code-sm text-xs text-on-surface-variant font-semibold uppercase tracking-wider truncate">
            {healthLoading
              ? 'CHECKING BACKEND AND GRAPH...'
              : `BACKEND: ${health?.application?.toUpperCase() || 'UNKNOWN'} • GRAPH: ${health?.graph || 'company_brain'} (${healthStatusText.toUpperCase()})`}
          </span>
        </div>
        <button
          onClick={onRefreshData}
          title="Refresh status"
          className="p-1 rounded-md text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
        >
          <span className="material-symbols-outlined text-[18px]">refresh</span>
        </button>
      </div>

      <div className="flex flex-col space-y-2 pt-1">
        <div className="flex items-center gap-2">
          <span className="px-2 py-0.5 rounded bg-primary-container text-on-primary-fixed font-label-sm text-xs uppercase tracking-wide font-bold">
            Intelligence Overview
          </span>
          <span className="font-code-sm text-xs text-secondary font-medium truncate">
            {health?.graph || 'company_brain'}
          </span>
        </div>
        <h1 className="font-headline-sm text-2xl md:text-3xl text-primary tracking-tight font-bold">
          Your company, understood.
        </h1>
        <p className="font-body-md text-sm md:text-base text-on-surface-variant max-w-2xl leading-relaxed">
          Ask graph-grounded questions and inspect live FalkorDB topology. Incomplete analysis views are labeled as such.
        </p>
      </div>

      <div className="flex flex-col space-y-3 bg-surface-container rounded-2xl p-5 md:p-6 border border-outline-variant/30 shadow-sm transition-colors">
        <form onSubmit={handleQuerySubmit} className="relative flex items-center">
          <span className="material-symbols-outlined absolute left-3.5 text-on-surface-variant text-[20px] pointer-events-none">
            troubleshoot
          </span>
          <input
            id="pulse-search-input"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-11 pr-24 py-3 rounded-xl bg-surface-container-lowest text-on-surface font-body-md text-sm placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:ring-2 focus:ring-secondary/40 focus:border-secondary transition-all"
            placeholder="Ask anything about systems, decisions, or repositories..."
            type="text"
          />
          <button
            type="submit"
            className="absolute right-2 px-4 py-2 rounded-lg bg-primary-container text-on-primary-container font-headline-sm text-xs font-bold uppercase tracking-wider hover:opacity-95 active:scale-95 transition-all cursor-pointer shadow-sm"
          >
            Ask
          </button>
        </form>

        <div className="flex items-center gap-2 overflow-x-auto py-1 no-scrollbar">
          <button
            onClick={() => onNavigateToAsk('What database is used for the payment service?')}
            className="flex-shrink-0 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 flex items-center gap-1.5 border border-outline-variant/30 cursor-pointer"
          >
            <span className="material-symbols-outlined text-secondary text-[15px]">psychology</span>
            <span>Payment service database</span>
          </button>
          <button
            onClick={() => onNavigateToAsk('Who owns Payment Service?')}
            className="flex-shrink-0 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 flex items-center gap-1.5 border border-outline-variant/30 cursor-pointer"
          >
            <span className="material-symbols-outlined text-secondary text-[15px]">account_tree</span>
            <span>Who owns Payment Service?</span>
          </button>
        </div>
      </div>

      <section className="space-y-3">
        <div className="flex items-center justify-between px-1">
          <span className="font-label-sm text-xs uppercase tracking-wider text-on-surface-variant font-bold">
            Company Intelligence Modules
          </span>
        </div>

        <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
          <div
            onClick={() => onNavigateToScreen?.('map')}
            className="group p-5 rounded-2xl bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 hover:border-secondary/50 shadow-sm transition-all cursor-pointer flex flex-col justify-between gap-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-secondary-container/25 text-secondary flex items-center justify-center border border-secondary/30 flex-shrink-0">
                  <span className="material-symbols-outlined text-[22px]">hub</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <h3 className="font-headline-sm text-base text-primary font-bold group-hover:text-secondary transition-colors">
                    Knowledge Map
                  </h3>
                  <span className="font-body-sm text-xs text-on-surface-variant mt-0.5">
                    Live nodes and relationships from FalkorDB
                  </span>
                </div>
              </div>
              <span className="material-symbols-outlined text-on-surface-variant group-hover:text-secondary group-hover:translate-x-0.5 transition-all text-[20px]">
                arrow_forward
              </span>
            </div>
            <div className="flex items-center justify-between pt-3 border-t border-outline-variant/20 text-xs">
              <span className="text-on-surface-variant font-code-sm">
                Endpoint: <strong className="text-primary">/api/brain/graph</strong>
              </span>
              <span className="font-label-sm text-secondary font-semibold group-hover:underline">Explore graph →</span>
            </div>
          </div>

          <div
            onClick={() => onNavigateToScreen?.('consequence')}
            className="group p-5 rounded-2xl bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 hover:border-outline-variant shadow-sm transition-all cursor-pointer flex flex-col justify-between gap-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-surface-container-high text-on-surface-variant flex items-center justify-center border border-outline-variant/30 flex-shrink-0">
                  <span className="material-symbols-outlined text-[22px]">alt_route</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2">
                    <h3 className="font-headline-sm text-base text-primary font-bold">
                      Consequence Explorer
                    </h3>
                    <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-lowest text-outline font-bold">
                      NOT IN THIS MVP
                    </span>
                  </div>
                  <span className="font-body-sm text-xs text-on-surface-variant mt-0.5">
                    Dedicated impact-path analysis is not available yet
                  </span>
                </div>
              </div>
              <span className="material-symbols-outlined text-on-surface-variant group-hover:translate-x-0.5 transition-all text-[20px]">
                arrow_forward
              </span>
            </div>
            <div className="flex items-center justify-between pt-3 border-t border-outline-variant/20 text-xs">
              <span className="text-on-surface-variant font-code-sm">Use Ask &amp; Investigate for grounded questions</span>
              <span className="font-label-sm text-on-surface-variant font-semibold group-hover:underline">View status →</span>
            </div>
          </div>

          <div
            onClick={() => onNavigateToScreen?.('ask')}
            className="group p-5 rounded-2xl bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 hover:border-primary-container shadow-sm transition-all cursor-pointer flex flex-col justify-between gap-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-primary-container/25 text-primary flex items-center justify-center border border-primary-container/30 flex-shrink-0">
                  <span className="material-symbols-outlined text-[22px]">troubleshoot</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <h3 className="font-headline-sm text-base text-primary font-bold">
                    Ask &amp; Investigate
                  </h3>
                  <span className="font-body-sm text-xs text-on-surface-variant mt-0.5">
                    Graph-grounded answers with citations from the backend
                  </span>
                </div>
              </div>
              <span className="material-symbols-outlined text-on-surface-variant group-hover:text-primary group-hover:translate-x-0.5 transition-all text-[20px]">
                arrow_forward
              </span>
            </div>
            <div className="flex items-center justify-between pt-3 border-t border-outline-variant/20 text-xs">
              <span className="text-on-surface-variant font-code-sm truncate">
                Model:{' '}
                <strong className="text-primary font-semibold">
                  {providerStatusLoading ? 'Checking...' : providerStatus?.configuredModel || 'Not reported'}
                </strong>
              </span>
              <span className="font-label-sm text-primary font-semibold group-hover:underline">Open investigator →</span>
            </div>
          </div>

          <div
            onClick={() => onNavigateToScreen?.('sources')}
            className="group p-5 rounded-2xl bg-surface-container hover:bg-surface-container-high border border-outline-variant/30 hover:border-tertiary/50 shadow-sm transition-all cursor-pointer flex flex-col justify-between gap-4"
          >
            <div className="flex items-start justify-between gap-3">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-xl bg-tertiary-container/25 text-tertiary flex items-center justify-center border border-tertiary/30 flex-shrink-0">
                  <span className="material-symbols-outlined text-[22px]">deployed_code</span>
                </div>
                <div className="flex flex-col min-w-0">
                  <h3 className="font-headline-sm text-base text-primary font-bold">
                    GitHub Sources
                  </h3>
                  <span className="font-body-sm text-xs text-on-surface-variant mt-0.5">
                    Import repositories when a GitHub token is configured
                  </span>
                </div>
              </div>
              <span className="material-symbols-outlined text-on-surface-variant group-hover:text-tertiary group-hover:translate-x-0.5 transition-all text-[20px]">
                arrow_forward
              </span>
            </div>
            <div className="flex items-center justify-between pt-3 border-t border-outline-variant/20 text-xs">
              <span className="text-on-surface-variant font-code-sm">Existing GitHub integration</span>
              <span className="font-label-sm text-tertiary font-semibold group-hover:underline">View sources →</span>
            </div>
          </div>
        </div>
      </section>

      <section className="flex flex-col space-y-3">
        <span className="font-label-sm text-xs uppercase tracking-wider text-on-surface-variant font-bold px-1">
          Current status
        </span>
        <div className="grid grid-cols-2 md:grid-cols-4 gap-3">
          <div className="bg-surface-container rounded-xl p-4 flex flex-col justify-between shadow-sm border border-outline-variant/25">
            <span className="font-label-sm text-xs text-on-surface-variant uppercase font-semibold">Graph</span>
            <span className="font-headline-sm text-2xl text-primary font-bold tracking-tight mt-2">
              {healthLoading ? '...' : isGraphOnline ? 'UP' : 'DOWN'}
            </span>
            <div className="mt-1 font-code-sm text-[11px] text-on-surface-variant truncate">
              {health?.message || 'Status not loaded'}
            </div>
          </div>
          <div className="bg-surface-container rounded-xl p-4 flex flex-col justify-between shadow-sm border border-outline-variant/25">
            <span className="font-label-sm text-xs text-on-surface-variant uppercase font-semibold">
              Payments chains
            </span>
            <span className="font-headline-sm text-2xl text-primary font-bold tracking-tight mt-2">
              {loadingContext ? '...' : paymentsContext ? contexts.length : '—'}
            </span>
            <div className="mt-1 font-code-sm text-[11px] text-on-surface-variant truncate">
              {paymentsContext ? 'From /api/demo/payments-context' : 'Demo context not loaded'}
            </div>
          </div>
          <div className="bg-surface-container rounded-xl p-4 flex flex-col justify-between shadow-sm border border-outline-variant/25">
            <span className="font-label-sm text-xs text-on-surface-variant uppercase font-semibold">LLM provider</span>
            <span className="font-headline-sm text-lg text-primary font-bold tracking-tight mt-2 truncate">
              {providerStatusLoading ? '...' : providerStatus?.selectedProvider || '—'}
            </span>
            <div className="mt-1 font-code-sm text-[11px] text-on-surface-variant truncate">
              {providerStatus?.configured ? 'Configured' : 'Status unknown'}
            </div>
          </div>
          <div className="bg-surface-container rounded-xl p-4 flex flex-col justify-between shadow-sm border border-outline-variant/25">
            <span className="font-label-sm text-xs text-on-surface-variant uppercase font-semibold">Model</span>
            <span className="font-headline-sm text-lg text-primary font-bold tracking-tight mt-2 truncate">
              {providerStatusLoading ? '...' : providerStatus?.configuredModel || '—'}
            </span>
            <div className="mt-1 font-code-sm text-[11px] text-on-surface-variant truncate">
              {providerStatus?.baseUrl || 'No endpoint reported'}
            </div>
          </div>
        </div>
      </section>

      {!loadingContext && !primaryContext && (
        <section className="p-5 rounded-2xl bg-surface-container border border-outline-variant/30 text-sm text-on-surface-variant">
          <span className="font-headline-sm text-sm text-primary font-bold block mb-1">
            No payments-context chains returned
          </span>
          {paymentsContext
            ? 'The demo endpoint responded, but it currently contains no decision chains to display.'
            : 'Could not load /api/demo/payments-context. Graph status above is still from /api/health.'}
        </section>
      )}

      {primaryContext && (
        <section className="flex flex-col space-y-3 pt-1">
          <span className="font-headline-sm text-sm text-primary font-bold uppercase tracking-wider">
            Sample graph chain
          </span>
          <div className="bg-surface-container rounded-xl p-4 flex flex-col space-y-2 shadow-sm border border-outline-variant/25">
            <div className="flex items-center justify-between gap-2">
              <span className="px-1.5 py-0.5 rounded bg-tertiary-container/30 text-tertiary font-code-sm text-[10px] font-bold tracking-wide uppercase">
                From payments context
              </span>
              {primaryContext.sourceId && (
                <span className="font-code-sm text-[10px] text-secondary font-bold">
                  Source ID: {primaryContext.sourceId}
                </span>
              )}
            </div>
            <div className="font-headline-sm text-sm text-primary font-semibold">{primaryContext.decision}</div>
            <p className="font-body-sm text-xs text-on-surface-variant">
              {primaryContext.project} uses {primaryContext.system}
              {primaryContext.documentTitle ? ` · ${primaryContext.documentTitle}` : ''}
            </p>
          </div>
        </section>
      )}
    </div>
  );
};
