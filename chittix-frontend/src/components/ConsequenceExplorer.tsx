import React, { useState } from 'react';
import type { PaymentsContextResponse, DecisionContext } from '../services/api';

interface ConsequenceExplorerProps {
  paymentsContext: PaymentsContextResponse | null;
  loading: boolean;
  onRefresh: () => void;
  onNavigateToAsk: (query: string) => void;
}

type DepthLevel = 1 | 2 | 3;

export const ConsequenceExplorer: React.FC<ConsequenceExplorerProps> = ({
  paymentsContext,
  loading,
  onRefresh,
  onNavigateToAsk,
}) => {
  const [depth, setDepth] = useState<DepthLevel>(1);

  const contexts = paymentsContext?.contexts || [];
  const primary: DecisionContext | undefined = contexts[0];

  return (
    <div className="flex flex-col w-full px-gutter-mobile md:px-gutter max-w-5xl mx-auto space-y-space-md pb-24 pt-4 text-on-surface">
      {/* Target Investigation Context Header Panel */}
      <section className="p-space-md rounded-2xl bg-surface-container-lowest border border-outline-variant/20 shadow-sm">
        <div className="flex items-center justify-between mb-space-xs">
          <div className="flex items-center gap-space-xs">
            <span className="inline-flex items-center gap-1.5 px-2 py-0.5 rounded-full bg-error/10 text-error font-label-sm text-label-sm border border-error/30">
              <span className="w-1.5 h-1.5 rounded-full bg-error animate-ping" />
              GRAPH TRACE ACTIVE
            </span>
            <span className="font-code-sm text-code-sm text-on-surface-variant font-mono">
              {primary ? primary.sourceId || 'RFC-TRACE' : 'STANDBY'}
            </span>
          </div>
          <div className="flex items-center gap-1 px-2 py-0.5 rounded bg-surface-container text-secondary font-code-sm text-code-sm border border-secondary/20">
            <span className="material-symbols-outlined text-[14px]">device_hub</span>
            <span>VERIFIED RELATIONSHIPS</span>
          </div>
        </div>

        <div className="space-y-1">
          <p className="font-headline-sm text-headline-sm text-primary font-semibold tracking-tight">
            See what depends on what.
          </p>
          <div className="flex flex-col gap-1 p-space-sm rounded-xl bg-surface-container-low border border-outline-variant/20">
            <div className="flex items-center gap-1.5 text-on-surface-variant">
              <span className="material-symbols-outlined text-[16px] text-secondary">database</span>
              <span className="font-label-sm text-label-sm text-on-surface-variant font-semibold">
                TARGET VECTOR:
              </span>
              <code className="font-code-sm text-code-sm text-primary-container font-medium">
                {primary ? primary.system : 'PostgreSQL'}
              </code>
            </div>
            <p className="font-body-sm text-body-sm text-on-surface-variant italic pl-5">
              “What would be affected if {primary ? primary.system : 'PostgreSQL'} became unavailable?”
            </p>
          </div>
        </div>
      </section>

      {/* Interactive Depth Filter Controls */}
      <div className="sticky top-16 z-30 py-2 bg-surface-dim/95 backdrop-blur">
        <div className="grid grid-cols-3 gap-1 p-1 bg-surface-container rounded-xl border border-outline-variant/20">
          <button
            onClick={() => setDepth(1)}
            className={`flex flex-col items-center justify-center py-2 px-1 rounded-lg transition-all ${
              depth === 1
                ? 'bg-primary-container text-on-primary-fixed shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-label-sm text-label-sm tracking-wider">DIRECT IMPACT</span>
            <span className="font-code-sm text-[10px] opacity-90">L1 · Direct Dependency</span>
          </button>
          <button
            onClick={() => setDepth(2)}
            className={`flex flex-col items-center justify-center py-2 px-1 rounded-lg transition-all ${
              depth === 2
                ? 'bg-primary-container text-on-primary-fixed shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-label-sm text-label-sm tracking-wider">SECONDARY TRACE</span>
            <span className="font-code-sm text-[10px] opacity-90">L2 · Decisions & Teams</span>
          </button>
          <button
            onClick={() => setDepth(3)}
            className={`flex flex-col items-center justify-center py-2 px-1 rounded-lg transition-all ${
              depth === 3
                ? 'bg-primary-container text-on-primary-fixed shadow-sm font-semibold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-label-sm text-label-sm tracking-wider">CASCADE (SIMULATION)</span>
            <span className="font-code-sm text-[10px] opacity-90">L3+ · Simulation Scope</span>
          </button>
        </div>
      </div>

      {/* Central Visual Topology Canvas */}
      {loading ? (
        <div className="w-full h-80 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 flex flex-col items-center justify-center gap-2 text-on-surface-variant">
          <span className="material-symbols-outlined text-[32px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-code-sm">Tracing graph dependency chain...</span>
        </div>
      ) : !primary ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 flex flex-col items-center justify-center text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[28px]">alt_route</span>
          </div>
          <span className="font-headline-sm text-headline-sm text-primary font-medium">
            No Impact Chain Available
          </span>
          <p className="font-body-sm text-body-sm text-on-surface-variant max-w-md">
            The FalkorDB backend is on standby or no relationships were returned. Run the backend to visualize consequence paths.
          </p>
          <button
            onClick={onRefresh}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md transition-colors border border-outline-variant/30 flex items-center gap-1.5"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry
          </button>
        </div>
      ) : (
        <section className="flex flex-col gap-space-md">
          {/* Central Target & Direct Impact */}
          <div className="relative w-full rounded-2xl bg-surface-container-lowest p-space-md border border-outline-variant/20 shadow-md">
            <div className="relative z-10 flex flex-col items-center">
              {/* Telemetry ribbon */}
              <div className="w-full flex items-center justify-between text-on-surface-variant font-code-sm text-code-sm mb-3">
                <span className="flex items-center gap-1">
                  <span className="w-2 h-2 rounded-full bg-error" />
                  ANALYZED TARGET: {primary.system}
                </span>
                <span className="text-tertiary-container font-mono">
                  RELATION: [:USES]
                </span>
              </div>

              {/* Target Node */}
              <div className="relative my-2">
                <div className="relative flex flex-col items-center justify-center w-28 h-28 rounded-full bg-surface-container-high text-center p-2 shadow-lg ring-2 ring-error/70">
                  <span className="material-symbols-outlined text-error text-[28px]">
                    database
                  </span>
                  <span className="font-headline-sm text-[12px] font-semibold text-primary leading-tight mt-0.5 truncate max-w-[90px]">
                    {primary.system}
                  </span>
                  <span className="font-code-sm text-[8px] uppercase tracking-wider text-error font-bold">
                    PRIMARY STORAGE
                  </span>
                </div>
              </div>

              {/* L1 Direct Impact Ring */}
              {(depth === 1 || depth === 2 || depth === 3) && (
                <div className="w-full mt-4 flex flex-col gap-2">
                  <div className="flex items-center justify-between text-[11px] font-code-sm text-secondary font-medium tracking-wide px-1">
                    <span className="flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">arrow_downward</span>
                      L1 DIRECT DEPENDENCY (VERIFIED)
                    </span>
                    <span className="px-1.5 py-0.5 rounded bg-surface-container text-primary font-semibold">
                      DIRECT CALLER
                    </span>
                  </div>

                  <div className="p-3 rounded-xl bg-surface-container-high border border-outline-variant/30 flex items-start justify-between">
                    <div className="flex flex-col">
                      <div className="flex items-center gap-2">
                        <span className="font-headline-sm text-headline-sm text-primary font-semibold">
                          {primary.project}
                        </span>
                        <span className="font-code-sm text-[9px] px-1.5 py-0.5 rounded bg-error-container/30 text-error font-bold">
                          HIGH DEPENDENCY
                        </span>
                      </div>
                      <p className="font-body-sm text-body-sm text-on-surface-variant mt-1">
                        Direct graph dependency: <code className="font-mono text-secondary">(:Project)-[:USES]-&gt;(:System)</code>. If {primary.system} is down, {primary.project} transactions fail.
                      </p>
                      <span className="font-code-sm text-[11px] text-outline mt-1">
                        Owner Team: <strong className="text-on-surface">{primary.ownerTeam}</strong>
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {/* L2 Secondary Trace */}
              {(depth === 2 || depth === 3) && (
                <div className="w-full mt-4 flex flex-col gap-2">
                  <div className="flex items-center justify-between text-[11px] font-code-sm text-secondary font-medium tracking-wide px-1">
                    <span className="flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">alt_route</span>
                      L2 ASSOCIATED DECISION & OWNERSHIP TRACE
                    </span>
                    <span className="px-1.5 py-0.5 rounded bg-surface-container text-on-surface-variant">
                      GOVERNANCE
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                    <div className="p-3 rounded-xl bg-surface-container border border-outline-variant/20 flex flex-col gap-1">
                      <span className="font-code-sm text-[10px] text-tertiary-container font-semibold uppercase">
                        AFFECTED DECISION
                      </span>
                      <span className="font-headline-sm text-label-md text-primary font-medium">
                        {primary.decision}
                      </span>
                      <span className="font-body-sm text-[11px] text-on-surface-variant">
                        Document: {primary.documentTitle} ({primary.sourceId || 'RFC'})
                      </span>
                    </div>

                    <div className="p-3 rounded-xl bg-surface-container border border-outline-variant/20 flex flex-col gap-1">
                      <span className="font-code-sm text-[10px] text-secondary font-semibold uppercase">
                        OWNERSHIP SQUAD
                      </span>
                      <span className="font-headline-sm text-label-md text-primary font-medium">
                        {primary.ownerTeam}
                      </span>
                      <span className="font-body-sm text-[11px] text-on-surface-variant">
                        Responsible for outage mitigation & failover procedures.
                      </span>
                    </div>
                  </div>
                </div>
              )}

              {/* L3 Cascade State (Honest Capability Notice) */}
              {depth === 3 && (
                <div className="w-full mt-4 p-4 rounded-xl bg-surface-container-low border border-secondary/30 flex flex-col gap-2 text-left">
                  <div className="flex items-center gap-2 text-secondary">
                    <span className="material-symbols-outlined text-[20px]">info</span>
                    <span className="font-headline-sm text-label-md font-semibold">
                      Factual Grounding Notice
                    </span>
                  </div>
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    In compliance with the factual source-of-truth constraint, Chittix only displays verified graph relationships stored in FalkorDB. Arbitrary deep failure cascade modeling (L3+) is not yet exposed by the backend schema for this cluster.
                  </p>
                  <button
                    onClick={() => onNavigateToAsk(`What would be affected if ${primary.system} became unavailable?`)}
                    className="self-start mt-1 px-3 py-1.5 rounded-lg bg-primary-container text-on-primary-fixed font-label-sm text-[12px] font-semibold flex items-center gap-1 active:scale-95 transition-transform"
                  >
                    <span className="material-symbols-outlined text-[15px]">troubleshoot</span>
                    Ask Graph Engine about this impact
                  </button>
                </div>
              )}
            </div>
          </div>
        </section>
      )}
    </div>
  );
};
