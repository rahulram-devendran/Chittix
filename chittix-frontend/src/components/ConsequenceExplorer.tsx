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

  // Deduplicate entities across all returned contexts to avoid duplicate nodes/edges
  const uniqueProjects = Array.from(
    new Map(contexts.map(c => [c.project, c])).values()
  );

  const uniqueDecisions = Array.from(
    new Map(contexts.map(c => [c.decision, c])).values()
  );

  const uniqueTeams = Array.from(
    new Map(contexts.map(c => [c.ownerTeam, c])).values()
  );

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      {/* Target Investigation Vector Card */}
      <section className="p-5 md:p-6 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm transition-colors">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-3">
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-error-container/30 text-error font-code-sm text-xs font-bold border border-error/30">
              <span className="w-2 h-2 rounded-full bg-error animate-ping" />
              OUTAGE IMPACT ANALYSIS
            </span>
            <span className="font-code-sm text-xs text-on-surface-variant font-mono">
              {primary ? primary.sourceId || 'DOC-001' : 'STANDBY'}
            </span>
          </div>
          <div className="flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-tertiary-container/30 text-tertiary font-code-sm text-xs font-bold border border-tertiary/30">
            <span className="material-symbols-outlined text-[15px]">verified</span>
            <span>VERIFIED FALKORDB TRUTH</span>
          </div>
        </div>

        <div className="space-y-3">
          <div>
            <h2 className="font-headline-sm text-xl md:text-2xl text-primary font-bold tracking-tight">
              See what depends on what.
            </h2>
            <p className="font-body-sm text-xs text-on-surface-variant mt-0.5">
              Trace downstream blast-radius directly through verified graph dependencies.
            </p>
          </div>

          <div className="flex flex-col gap-1.5 p-4 rounded-xl bg-surface-container-low border border-outline-variant/25">
            <div className="flex flex-wrap items-center gap-2 text-on-surface-variant">
              <span className="material-symbols-outlined text-[18px] text-secondary">database</span>
              <span className="font-label-sm text-xs text-outline font-bold uppercase tracking-wider">
                ANALYSIS TARGET VECTOR:
              </span>
              <span className="font-code-sm text-xs text-secondary font-bold px-2 py-0.5 rounded bg-surface-container border border-secondary/20">
                {primary ? primary.system : 'PostgreSQL'}
              </span>
            </div>
            <p className="font-body-md text-sm text-on-surface italic pl-6 font-medium">
              “What critical business capabilities fail if <strong className="text-secondary">{primary ? primary.system : 'PostgreSQL'}</strong> becomes unavailable?”
            </p>
          </div>
        </div>
      </section>

      {/* Interactive Depth Filter Tabs - L1 = level 1 only; L2 = levels 1–2; L3 = levels 1–3 */}
      <div className="sticky top-16 z-20 py-2 bg-surface/95 backdrop-blur-md">
        <div className="grid grid-cols-3 gap-2 p-1.5 bg-surface-container rounded-xl border border-outline-variant/30 shadow-sm">
          <button
            onClick={() => setDepth(1)}
            className={`flex flex-col items-center justify-center py-2 px-2 rounded-lg transition-all cursor-pointer ${
              depth === 1
                ? 'bg-primary-container text-on-primary-container shadow-sm font-bold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-headline-sm text-xs tracking-wider uppercase">L1 · Direct Impact</span>
            <span className="font-code-sm text-[10px] opacity-80 font-medium">Level 1 Only ({uniqueProjects.length} Callers)</span>
          </button>
          <button
            onClick={() => setDepth(2)}
            className={`flex flex-col items-center justify-center py-2 px-2 rounded-lg transition-all cursor-pointer ${
              depth === 2
                ? 'bg-primary-container text-on-primary-container shadow-sm font-bold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-headline-sm text-xs tracking-wider uppercase">L2 · Governance</span>
            <span className="font-code-sm text-[10px] opacity-80 font-medium">Levels 1–2 (Decisions & Squads)</span>
          </button>
          <button
            onClick={() => setDepth(3)}
            className={`flex flex-col items-center justify-center py-2 px-2 rounded-lg transition-all cursor-pointer ${
              depth === 3
                ? 'bg-primary-container text-on-primary-container shadow-sm font-bold'
                : 'text-on-surface-variant hover:text-on-surface hover:bg-surface-container-high'
            }`}
          >
            <span className="font-headline-sm text-xs tracking-wider uppercase">L3 · Simulation</span>
            <span className="font-code-sm text-[10px] opacity-80 font-medium">Levels 1–3 (Full Cascade)</span>
          </button>
        </div>
      </div>

      {/* Central Visual Topology Canvas */}
      {loading ? (
        <div className="w-full h-80 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center gap-3 text-on-surface-variant shadow-sm">
          <span className="material-symbols-outlined text-[36px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-xs">Tracing graph dependency chain...</span>
        </div>
      ) : !primary ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center text-center gap-3 shadow-sm">
          <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[28px]">alt_route</span>
          </div>
          <span className="font-headline-sm text-base text-primary font-bold">
            No Impact Chain Available
          </span>
          <p className="font-body-sm text-xs text-on-surface-variant max-w-md">
            The FalkorDB backend is on standby or no relationships were returned. Run the backend to visualize consequence paths.
          </p>
          <button
            onClick={onRefresh}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/30 flex items-center gap-1.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry
          </button>
        </div>
      ) : (
        <section className="flex flex-col gap-6">
          {/* Target Node Focus Display */}
          <div className="relative w-full rounded-2xl bg-surface-container p-5 md:p-6 border border-outline-variant/30 shadow-sm transition-colors">
            <div className="relative z-10 flex flex-col items-center">
              {/* Telemetry bar */}
              <div className="w-full flex items-center justify-between text-on-surface-variant font-code-sm text-xs mb-3">
                <span className="flex items-center gap-1.5 font-semibold">
                  <span className="w-2 h-2 rounded-full bg-error" />
                  ANALYZED FAULT TARGET: {primary.system}
                </span>
                <span className="text-secondary font-mono font-bold">
                  ACTIVE FILTER: {depth === 1 ? 'LEVEL 1 ONLY' : depth === 2 ? 'LEVELS 1–2' : 'LEVELS 1–3'}
                </span>
              </div>

              {/* Target Node Avatar */}
              <div className="relative my-3">
                <div className="relative flex flex-col items-center justify-center w-32 h-32 rounded-full bg-surface-container-high text-center p-3 shadow-md ring-4 ring-error/50">
                  <span className="material-symbols-outlined text-error text-[32px]">
                    database
                  </span>
                  <span className="font-headline-sm text-sm font-bold text-primary leading-tight mt-1 truncate max-w-[100px]">
                    {primary.system}
                  </span>
                  <span className="font-code-sm text-[9px] uppercase tracking-wider text-error font-bold mt-0.5">
                    PRIMARY STORAGE
                  </span>
                </div>
              </div>

              {/* L1 Direct Impact Ring (Included in L1, L2, L3) */}
              <div className="w-full mt-4 flex flex-col gap-2.5">
                <div className="flex items-center justify-between text-xs font-code-sm text-secondary font-bold tracking-wide px-1">
                  <span className="flex items-center gap-1.5 text-tertiary">
                    <span className="material-symbols-outlined text-[15px]">verified</span>
                    L1 DIRECT DEPENDENCY (VERIFIED IN FALKORDB)
                  </span>
                  <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-primary font-bold">
                    DIRECT CALLER ({uniqueProjects.length})
                  </span>
                </div>

                <div className="grid grid-cols-1 gap-2.5">
                  {uniqueProjects.map((proj, pIdx) => (
                    <div
                      key={pIdx}
                      className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-2"
                    >
                      <div className="flex flex-wrap items-center justify-between gap-2">
                        <div className="flex items-center gap-2">
                          <span className="font-headline-sm text-base text-primary font-bold">
                            {proj.project}
                          </span>
                          <span className="font-code-sm text-[10px] px-2 py-0.5 rounded-md bg-error-container/30 text-error font-bold border border-error/20">
                            DIRECT 1-HOP CALLER
                          </span>
                        </div>
                        <span className="font-code-sm text-xs text-outline font-medium">
                          Owner: <strong className="text-primary font-semibold">{proj.ownerTeam}</strong>
                        </span>
                      </div>

                      <p className="font-body-sm text-xs text-on-surface-variant leading-relaxed">
                        Verified graph fact: <code className="font-mono text-secondary font-semibold break-words">(:Project &#123;name: '{proj.project}'&#125;)-[:USES]-&gt;(:System &#123;name: '{proj.system}'&#125;)</code>. If {proj.system} experiences a disruption, {proj.project} transactions directly fail without failover.
                      </p>
                    </div>
                  ))}
                </div>
              </div>

              {/* L2 Secondary Trace (Included in L2 and L3 only) */}
              {(depth === 2 || depth === 3) && (
                <div className="w-full mt-5 flex flex-col gap-2.5 animate-fadeIn">
                  <div className="flex items-center justify-between text-xs font-code-sm text-secondary font-bold tracking-wide px-1">
                    <span className="flex items-center gap-1.5 text-tertiary">
                      <span className="material-symbols-outlined text-[15px]">verified</span>
                      L2 ASSOCIATED GOVERNANCE & SQUAD TRACE (VERIFIED IN FALKORDB)
                    </span>
                    <span className="px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant font-bold">
                      GOVERNANCE
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                    {uniqueDecisions.map((dec, dIdx) => (
                      <div
                        key={dIdx}
                        className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-1.5"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-code-sm text-[10px] text-secondary font-bold uppercase tracking-wider">
                            AFFECTED DECISION
                          </span>
                          <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-tertiary font-bold">
                            VERIFIED
                          </span>
                        </div>
                        <span className="font-headline-sm text-sm text-primary font-bold">
                          {dec.decision}
                        </span>
                        <span className="font-body-sm text-xs text-on-surface-variant">
                          Supporting Spec: <strong className="text-primary">{dec.documentTitle}</strong> ({dec.sourceId})
                        </span>
                        <span className="font-code-sm text-[10px] text-outline italic">
                          Fact: Decision affects {dec.project}. (Graph records no separate written rationale).
                        </span>
                      </div>
                    ))}

                    {uniqueTeams.map((tm, tIdx) => (
                      <div
                        key={tIdx}
                        className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-1.5"
                      >
                        <div className="flex items-center justify-between">
                          <span className="font-code-sm text-[10px] text-secondary font-bold uppercase tracking-wider">
                            PRIMARY ESCALATION SQUAD
                          </span>
                          <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-tertiary font-bold">
                            VERIFIED
                          </span>
                        </div>
                        <span className="font-headline-sm text-sm text-primary font-bold">
                          {tm.ownerTeam}
                        </span>
                        <span className="font-body-sm text-xs text-on-surface-variant">
                          Owning team for {tm.project}. Responsible squad for incident coordination and cluster health.
                        </span>
                        <span className="font-code-sm text-[10px] text-outline italic">
                          Note: Ownership indicates maintenance squad; does not assert decision sign-off.
                        </span>
                      </div>
                    ))}
                  </div>
                </div>
              )}

              {/* L3 Cascade State (Included in L3 only - Clearly Labeled as Simulation) */}
              {depth === 3 && (
                <div className="w-full mt-5 p-5 rounded-xl bg-surface-container-low border border-secondary/40 flex flex-col gap-3 text-left animate-fadeIn">
                  <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-outline-variant/20">
                    <div className="flex items-center gap-2 text-secondary">
                      <span className="material-symbols-outlined text-[20px]">science</span>
                      <span className="font-headline-sm text-sm font-bold uppercase tracking-wider">
                        Hypothetical Cascade Modeling (Simulated Scenario)
                      </span>
                    </div>
                    <span className="font-code-sm text-[10px] uppercase px-2 py-0.5 rounded-full bg-secondary-container/30 text-secondary border border-secondary/30 font-bold">
                      Not Stored in Graph
                    </span>
                  </div>

                  <p className="font-body-sm text-xs text-on-surface leading-relaxed">
                    <strong>Ground-Truth Integrity Guarantee:</strong> In accordance with Chittix factual retrieval principles, only L1 direct dependencies and L2 governance paths are verified database facts in FalkorDB. Multi-hop cascading failures (L3) represent simulated risk projections, not verified historical records.
                  </p>

                  <div className="p-3.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col gap-1.5">
                    <span className="font-code-sm text-[11px] text-outline uppercase font-semibold">
                      Projected Blast Radius (Simulated Projection):
                    </span>
                    <div className="flex flex-wrap gap-2 pt-0.5">
                      <span className="px-2.5 py-1 rounded bg-surface-container-high text-xs font-code-sm text-on-surface font-medium border border-outline-variant/30">
                        Checkout Workflow (Consumer of {primary.project})
                      </span>
                      <span className="px-2.5 py-1 rounded bg-surface-container-high text-xs font-code-sm text-on-surface font-medium border border-outline-variant/30">
                        Merchant Payout API (Consumer of {primary.project})
                      </span>
                      <span className="px-2.5 py-1 rounded bg-surface-container-high text-xs font-code-sm text-on-surface font-medium border border-outline-variant/30">
                        Audit & Ledger Services
                      </span>
                    </div>
                  </div>

                  <button
                    onClick={() => onNavigateToAsk(`What would be affected if ${primary.system} became unavailable?`)}
                    className="self-start mt-1 px-4 py-2 rounded-lg bg-primary-container text-on-primary-container font-headline-sm text-xs font-bold uppercase tracking-wider flex items-center gap-1.5 active:scale-95 transition-transform cursor-pointer shadow-sm"
                  >
                    <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                    Ask Graph Engine about this impact
                  </button>
                </div>
              )}
            </div>
          </div>

          {/* Traceable Evidence Paths - Non-truncated */}
          {primary.evidencePath && primary.evidencePath.length > 0 && (
            <div className="p-5 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col gap-3">
              <span className="font-headline-sm text-sm font-bold text-primary uppercase tracking-wider">
                Traceable Graph Evidence Path
              </span>
              <div className="flex flex-col gap-2">
                {primary.evidencePath.map((step, sIdx) => (
                  <div
                    key={sIdx}
                    className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/20 font-code-sm text-xs text-on-surface break-words flex items-start gap-2"
                  >
                    <span className="w-5 h-5 rounded-full bg-surface-container-high text-[11px] flex items-center justify-center font-bold text-secondary flex-shrink-0 mt-0.5">
                      {sIdx + 1}
                    </span>
                    <span className="flex-1 break-words leading-relaxed">{step}</span>
                  </div>
                ))}
              </div>
            </div>
          )}
        </section>
      )}
    </div>
  );
};
