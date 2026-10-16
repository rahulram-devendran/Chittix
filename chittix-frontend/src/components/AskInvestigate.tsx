import React, { useState, useEffect } from 'react';
import { askQuestion, type AskResponse, type DecisionContext, type ProviderStatusResponse } from '../services/api';

interface AskInvestigateProps {
  initialQuestion?: string;
  onSelectEvidence?: (sourceId: string) => void;
  providerStatus?: ProviderStatusResponse | null;
}

const SAMPLE_QUESTIONS = [
  'Why did we choose PostgreSQL for the Payment Service?',
  'Who owns Payment Service?',
  'What is our vacation policy?',
];

/**
 * Parses Cypher fact strings like `(Decision: Use PostgreSQL for payments)-[AFFECTS]->(Project: Payment Service)`
 * into structured components for clean, wrap-safe UI rendering.
 */
function parseCypherTriple(fact: string): {
  sourceType: string;
  sourceName: string;
  relation: string;
  targetType: string;
  targetName: string;
} | null {
  const match = fact.match(/^\(([^:]+):\s*([^)]+)\)-\[([^\]]+)\]->\(([^:]+):\s*([^)]+)\)$/);
  if (!match) return null;
  return {
    sourceType: match[1].trim(),
    sourceName: match[2].trim(),
    relation: match[3].trim(),
    targetType: match[4].trim(),
    targetName: match[5].trim(),
  };
}

export const AskInvestigate: React.FC<AskInvestigateProps> = ({
  initialQuestion,
  providerStatus,
}) => {
  const [question, setQuestion] = useState(
    initialQuestion || 'Why did we choose PostgreSQL for the Payment Service?'
  );
  const [loading, setLoading] = useState(false);
  const [response, setResponse] = useState<AskResponse | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [latencyMs, setLatencyMs] = useState<number | null>(null);

  const handleAsk = React.useCallback(async (queryToAsk?: string) => {
    const q = (queryToAsk !== undefined ? queryToAsk : question).trim();
    if (!q) return;

    setLoading(true);
    setError(null);
    const start = performance.now();

    try {
      const res = await askQuestion(q);
      const elapsed = Math.round(performance.now() - start);
      setLatencyMs(elapsed);
      setResponse(res);
    } catch (err: unknown) {
      const message = err instanceof Error ? err.message : 'Failed to query the graph backend.';
      setError(message);
      setResponse(null);
    } finally {
      setLoading(false);
    }
  }, [question]);

  // Sync if initialQuestion changed from parent navigation
  useEffect(() => {
    if (initialQuestion) {
      setQuestion(initialQuestion);
      handleAsk(initialQuestion);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [initialQuestion]);

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleAsk();
  };

  const isLlmGrounded = response?.answerType === 'LLM_GROUNDED';
  const isDeterministicFallback = response?.answerType === 'DETERMINISTIC_FALLBACK';
  const isInsufficient = response?.answerType === 'INSUFFICIENT_EVIDENCE' || (response && !response.answered);

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      {/* Query & Controls Section */}
      <section className="flex flex-col gap-4 bg-surface-container rounded-2xl p-5 md:p-6 border border-outline-variant/30 shadow-sm transition-colors">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-2">
            <span className="w-2.5 h-2.5 rounded-full bg-secondary animate-pulse" />
            <span className="font-code-sm text-xs text-on-surface-variant uppercase tracking-wider font-semibold">
              Investigative Cypher &amp; Intelligence Query
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="font-code-sm text-xs px-2 py-0.5 rounded-md bg-surface-container-high text-secondary border border-secondary/20 font-medium">
              Graph: FalkorDB
            </span>
            {providerStatus?.configuredModel && (
              <span className="font-code-sm text-xs px-2 py-0.5 rounded-md bg-secondary-container/20 text-secondary border border-secondary/20 hidden sm:inline font-medium">
                Model: {providerStatus.configuredModel}
              </span>
            )}
          </div>
        </div>

        {/* Input Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <div className="relative flex items-center">
            <span className="material-symbols-outlined text-[20px] text-on-surface-variant absolute left-3.5 pointer-events-none">
              troubleshoot
            </span>
            <input
              value={question}
              onChange={e => setQuestion(e.target.value)}
              placeholder="Ask anything about systems, decisions, or projects..."
              className="w-full pl-11 pr-28 py-3 rounded-xl bg-surface-container-lowest text-on-surface font-body-md text-sm placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:ring-2 focus:ring-secondary/40 focus:border-secondary transition-all"
            />
            <button
              type="submit"
              disabled={loading || !question.trim()}
              className="absolute right-2 px-4 py-2 rounded-lg bg-primary-container text-on-primary-container font-headline-sm text-xs font-bold uppercase tracking-wider disabled:opacity-40 active:scale-95 transition-all shadow-sm cursor-pointer"
            >
              {loading ? 'Querying...' : 'Query'}
            </button>
          </div>
        </form>

        {/* Quick Suggestion Pills */}
        <div className="flex items-center gap-2 overflow-x-auto py-1 no-scrollbar">
          <span className="text-xs font-code-sm text-outline flex-shrink-0 font-medium">Try:</span>
          {SAMPLE_QUESTIONS.map((sq, idx) => (
            <button
              key={idx}
              onClick={() => {
                setQuestion(sq);
                handleAsk(sq);
              }}
              className="flex-shrink-0 px-3 py-1 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 border border-outline-variant/30 hover:border-outline-variant cursor-pointer truncate max-w-sm"
            >
              {sq}
            </button>
          ))}
        </div>

        {/* Latency & Backend Telemetry Bar */}
        <div className="flex flex-wrap items-center justify-between gap-2 pt-2 border-t border-outline-variant/20 text-xs font-code-sm text-on-surface-variant">
          <div className="flex flex-wrap items-center gap-2">
            <span className="px-2 py-0.5 rounded bg-surface-container-high text-secondary font-medium">
              FalkorDB Graph Engine
            </span>
            {isLlmGrounded ? (
              <span className="text-secondary flex items-center gap-1 font-medium">
                • LLM Runtime: {providerStatus?.selectedProvider || 'ollama'} ({providerStatus?.configuredModel || 'qwen2.5:1.5b'})
              </span>
            ) : isDeterministicFallback ? (
              <span className="text-amber-500 flex items-center gap-1 font-medium">
                • Fallback: Deterministic Cypher Graph Assembly
              </span>
            ) : (
              <span className="text-outline hidden sm:inline">
                • Active Model: {providerStatus?.configuredModel || 'Local LLM'}
              </span>
            )}
          </div>
          {latencyMs !== null && (
            <span className="flex items-center gap-1 font-mono text-secondary font-medium">
              <span className="material-symbols-outlined text-[14px]">speed</span> {latencyMs}ms
            </span>
          )}
        </div>
      </section>

      {/* Loading State */}
      {loading && (
        <div className="w-full p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center gap-3 text-on-surface-variant shadow-sm">
          <span className="material-symbols-outlined text-[36px] text-secondary animate-spin">
            progress_activity
          </span>
          <div className="flex flex-col items-center gap-1 text-center">
            <span className="font-headline-sm text-sm font-semibold text-primary">
              Executing Cypher Graph Query &amp; Synthesis
            </span>
            <span className="font-code-sm text-xs text-on-surface-variant">
              Traversing (Decision)-[:AFFECTS]-&gt;(Project)-[:USES]-&gt;(System) paths in FalkorDB...
            </span>
          </div>
        </div>
      )}

      {/* Error State */}
      {error && !loading && (
        <div className="p-5 md:p-6 rounded-2xl bg-error-container/20 border border-error/40 flex flex-col gap-3 shadow-sm">
          <div className="flex items-center gap-2.5 text-error">
            <span className="material-symbols-outlined text-[24px]">error</span>
            <span className="font-headline-sm text-base font-semibold">
              Graph Query Failed
            </span>
          </div>
          <p className="font-body-md text-sm text-on-surface-variant">{error}</p>
          <p className="font-body-sm text-xs text-outline">
            Verify that the Spring Boot backend (`mvnw.cmd spring-boot:run`) is active on port 8080 and reachable.
          </p>
          <button
            onClick={() => handleAsk()}
            className="self-start mt-1 px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/40 flex items-center gap-1.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry Graph Query
          </button>
        </div>
      )}

      {/* Results Viewport */}
      {response && !loading && (
        <div className="space-y-6">
          {/* TIER 1: Synthesized Resolution Card */}
          <section className="bg-surface-container rounded-2xl p-5 md:p-6 space-y-4 border border-outline-variant/30 shadow-sm">
            <div className="flex flex-wrap items-center justify-between gap-2 pb-2 border-b border-outline-variant/20">
              <div className="flex items-center gap-2">
                <span className="material-symbols-outlined text-[20px] text-secondary">
                  psychology
                </span>
                <span className="font-headline-sm text-sm font-bold text-primary tracking-tight">
                  Graph Resolution &amp; Synthesis
                </span>
              </div>
              <div className="flex flex-wrap items-center gap-2">
                <span className="font-code-sm text-[11px] px-2 py-0.5 rounded-full bg-surface-container-high text-on-surface-variant border border-outline-variant/30 font-medium">
                  {response.answered ? 'CONFIRMED EVIDENCE' : 'UNRESOLVED'}
                </span>
                {isLlmGrounded ? (
                  <span className="font-code-sm text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-container/25 text-secondary border border-secondary/30 font-semibold flex items-center gap-1">
                    <span className="material-symbols-outlined text-[13px]">psychology</span>
                    LLM-Grounded ({providerStatus?.configuredModel || 'qwen2.5:1.5b'})
                  </span>
                ) : isDeterministicFallback ? (
                  <span className="font-code-sm text-[11px] px-2.5 py-0.5 rounded-full bg-amber-500/10 text-amber-500 border border-amber-500/30 font-semibold flex items-center gap-1">
                    <span className="material-symbols-outlined text-[13px]">schema</span>
                    Deterministic Fallback · No LLM
                  </span>
                ) : (
                  <span className="font-code-sm text-[11px] px-2.5 py-0.5 rounded-full bg-secondary-container/25 text-secondary border border-secondary/30 font-semibold">
                    {response.answerType || 'Rule-Based Graph Resolution'}
                  </span>
                )}
              </div>
            </div>

            {/* Synthesized Answer Body */}
            <div className="space-y-2">
              <p className="font-body-md text-base text-primary font-medium leading-relaxed">
                {response.answer}
              </p>
              <p className="text-xs font-code-sm text-outline italic">
                {isLlmGrounded
                  ? 'Notice: Synthesized by local LLM grounded exclusively in verified FalkorDB facts and indexed documentation. Unstated claims and inferred approvals are omitted.'
                  : isDeterministicFallback
                  ? 'Notice: Assembled deterministically from FalkorDB graph relationships. Generative LLM was unavailable or returned fallback.'
                  : 'Notice: Factual query against FalkorDB knowledge graph.'}
              </p>
            </div>
          </section>

          {/* Conditional: Answered with Evidence VS Insufficient Evidence */}
          {!isInsufficient && response.answered ? (
            <div className="space-y-6">
              {/* TIER 2: Confirmed Graph Facts (Ground Truth) */}
              {response.facts && response.facts.length > 0 && (
                <section className="bg-surface-container rounded-2xl p-5 md:p-6 space-y-3 border border-outline-variant/30 shadow-sm">
                  <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[18px] text-tertiary">
                        verified
                      </span>
                      <span className="font-headline-sm text-sm font-bold text-primary tracking-tight uppercase">
                        Confirmed Graph Facts (FalkorDB Truth)
                      </span>
                    </div>
                    <span className="font-code-sm text-xs px-2.5 py-0.5 rounded-full bg-tertiary-container/30 text-tertiary font-bold border border-tertiary/30">
                      {response.facts.length} Verified Facts
                    </span>
                  </div>

                  <div className="space-y-2 pt-1">
                    {response.facts.map((fact, i) => {
                      const triple = parseCypherTriple(fact);
                      return (
                        <div
                          key={i}
                          className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col sm:flex-row sm:items-center justify-between gap-2 transition-colors hover:border-outline-variant/50"
                        >
                          {triple ? (
                            <div className="flex flex-wrap items-center gap-2 text-xs font-body-md">
                              <span className="px-2 py-0.5 rounded bg-surface-container-high text-secondary font-mono font-semibold">
                                {triple.sourceType}: {triple.sourceName}
                              </span>
                              <span className="text-outline font-bold flex items-center gap-0.5 font-mono">
                                ──[{triple.relation}]──▶
                              </span>
                              <span className="px-2 py-0.5 rounded bg-primary-container/30 text-primary font-mono font-semibold">
                                {triple.targetType}: {triple.targetName}
                              </span>
                            </div>
                          ) : (
                            <div className="font-code-sm text-xs text-on-surface break-words">
                              {fact}
                            </div>
                          )}
                          <span className="self-end sm:self-center font-code-sm text-[10px] uppercase px-1.5 py-0.5 rounded bg-tertiary-container/20 text-tertiary font-bold tracking-wider">
                            Verified
                          </span>
                        </div>
                      );
                    })}
                  </div>
                </section>
              )}

              {/* TIER 3: Grounded Decision Chains */}
              {response.decisions && response.decisions.length > 0 && (
                <section className="bg-surface-container rounded-2xl p-5 md:p-6 space-y-4 border border-outline-variant/30 shadow-sm">
                  <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[18px] text-secondary">
                        account_tree
                      </span>
                      <span className="font-headline-sm text-sm font-bold text-primary tracking-tight uppercase">
                        Grounded Decision Chains
                      </span>
                    </div>
                    <span className="font-code-sm text-xs text-on-surface-variant">
                      Architecture Governance
                    </span>
                  </div>

                  <div className="space-y-4">
                    {response.decisions.map((dec: DecisionContext, idx: number) => (
                      <div
                        key={idx}
                        className="p-4 md:p-5 rounded-xl bg-surface-container-low border border-outline-variant/30 flex flex-col gap-3"
                      >
                        <div className="flex flex-wrap items-center justify-between gap-2">
                          <span className="font-headline-sm text-base text-primary font-bold">
                            {dec.decision}
                          </span>
                          {dec.sourceId && (
                            <span className="font-code-sm text-xs px-2 py-0.5 rounded-md bg-surface-container-high text-secondary border border-secondary/20 font-semibold">
                              Source ID: {dec.sourceId}
                            </span>
                          )}
                        </div>

                        {/* Triad Metric Pills */}
                        <div className="grid grid-cols-1 sm:grid-cols-3 gap-2">
                          <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                            <span className="font-label-sm text-[10px] text-outline uppercase font-semibold">
                              Affected Project
                            </span>
                            <span className="font-headline-sm text-sm text-primary font-semibold truncate">
                              {dec.project}
                            </span>
                          </div>
                          <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                            <span className="font-label-sm text-[10px] text-outline uppercase font-semibold">
                              Owning Squad
                            </span>
                            <span className="font-headline-sm text-sm text-primary font-semibold truncate">
                              {dec.ownerTeam || 'Unassigned'}
                            </span>
                            <span className="text-[10px] text-outline mt-0.5 leading-tight">
                              Maintenance squad; ownership does not imply decision approval
                            </span>
                          </div>
                          <div className="p-2.5 rounded-lg bg-surface-container border border-outline-variant/20 flex flex-col">
                            <span className="font-label-sm text-[10px] text-outline uppercase font-semibold">
                              Target System
                            </span>
                            <span className="font-headline-sm text-sm text-secondary font-semibold truncate">
                              {dec.system}
                            </span>
                          </div>
                        </div>

                        {/* Discrete Step-by-Step Chain Representation */}
                        {dec.evidencePath && dec.evidencePath.length > 0 && (
                          <div className="mt-1 pt-2 border-t border-outline-variant/20 flex flex-col gap-1.5">
                            <span className="font-label-sm text-[10px] text-outline uppercase tracking-wider font-semibold">
                              Factual Chain Steps:
                            </span>
                            <div className="flex flex-col gap-1.5">
                              {dec.evidencePath.map((step, sIdx) => {
                                const triple = parseCypherTriple(step);
                                return (
                                  <div
                                    key={sIdx}
                                    className="flex flex-wrap items-center gap-1.5 text-xs font-code-sm text-on-surface-variant bg-surface-container px-2.5 py-1.5 rounded-lg border border-outline-variant/20"
                                  >
                                    <span className="w-4 h-4 rounded-full bg-surface-container-high text-[10px] flex items-center justify-center font-bold text-outline">
                                      {sIdx + 1}
                                    </span>
                                    {triple ? (
                                      <>
                                        <span className="text-primary font-medium">({triple.sourceType}: {triple.sourceName})</span>
                                        <span className="text-secondary font-semibold font-mono">:[{triple.relation}]</span>
                                        <span className="text-primary font-medium">({triple.targetType}: {triple.targetName})</span>
                                      </>
                                    ) : (
                                      <span className="break-words">{step}</span>
                                    )}
                                  </div>
                                );
                              })}
                            </div>
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* TIER 4: Supporting Evidence Documents */}
              {response.evidence && response.evidence.length > 0 && (
                <section className="bg-surface-container rounded-2xl p-5 md:p-6 space-y-3 border border-outline-variant/30 shadow-sm">
                  <div className="flex items-center justify-between pb-2 border-b border-outline-variant/20">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-[18px] text-secondary">
                        description
                      </span>
                      <span className="font-headline-sm text-sm font-bold text-primary tracking-tight uppercase">
                        Supporting Evidence Documents
                      </span>
                    </div>
                    <span className="font-code-sm text-xs text-on-surface-variant">
                      Source of Truth
                    </span>
                  </div>

                  <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
                    {response.evidence.map((ev, idx) => (
                      <div
                        key={idx}
                        className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/30 flex items-start gap-3 hover:border-secondary/40 transition-colors"
                      >
                        <div className="w-9 h-9 rounded-lg bg-secondary-container/30 border border-secondary/30 flex items-center justify-center flex-shrink-0 text-secondary">
                          <span className="material-symbols-outlined text-[20px]">article</span>
                        </div>
                        <div className="flex flex-col min-w-0">
                          <span className="font-headline-sm text-sm text-primary font-semibold truncate">
                            {ev.documentTitle}
                          </span>
                          <span className="font-code-sm text-xs text-on-surface-variant mt-0.5">
                            Source ID: <strong className="text-secondary">{ev.sourceId}</strong>
                          </span>
                          <span className="font-label-sm text-[10px] text-tertiary mt-1 flex items-center gap-1">
                            <span className="w-1.5 h-1.5 rounded-full bg-tertiary" />
                            Grounded in FalkorDB
                          </span>
                        </div>
                      </div>
                    ))}
                  </div>
                </section>
              )}

              {/* TIER 5: Evidence Boundaries & Missing Evidence */}
              <section className="bg-surface-container/60 rounded-2xl p-5 md:p-6 border border-outline-variant/30 space-y-3">
                <div className="flex items-center gap-2 pb-2 border-b border-outline-variant/20">
                  <span className="material-symbols-outlined text-[18px] text-outline">
                    policy
                  </span>
                  <span className="font-headline-sm text-xs font-bold text-on-surface-variant uppercase tracking-wider">
                    Evidence Boundaries &amp; Integrity Guidelines
                  </span>
                </div>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 text-xs font-body-sm text-on-surface-variant">
                  <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                    <span className="font-semibold text-primary flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[15px] text-tertiary">check_circle</span>
                      Stored in Graph
                    </span>
                    <p className="text-on-surface-variant leading-relaxed">
                      System architecture relationships, affected services, linked document IDs, and primary owning squads.
                    </p>
                  </div>
                  <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-1">
                    <span className="font-semibold text-outline flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[15px] text-error/80">info</span>
                      Missing Evidence / Not In Graph
                    </span>
                    <p className="text-on-surface-variant leading-relaxed">
                      Meeting recordings, approval signatures, ratification dates, and external benchmark data are not stored in FalkorDB.
                    </p>
                  </div>
                </div>
              </section>
            </div>
          ) : (
            /* Insufficient Evidence State Card */
            <section className="bg-surface-container rounded-2xl p-6 border border-outline-variant/30 shadow-sm flex flex-col gap-4">
              <div className="flex items-center gap-2.5 text-on-surface-variant pb-2 border-b border-outline-variant/20">
                <span className="material-symbols-outlined text-[22px] text-secondary">
                  help_outline
                </span>
                <span className="font-headline-sm text-sm font-bold uppercase tracking-wider text-primary">
                  Insufficient Graph Evidence State
                </span>
              </div>

              <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col gap-2">
                <p className="font-body-md text-sm text-on-surface leading-relaxed">
                  {response.answer}
                </p>
                <p className="font-body-sm text-xs text-outline">
                  FalkorDB traversed the active graph cluster but found no matching Decision, Project, or System node connected to this query.
                </p>
              </div>

              <div className="flex flex-col gap-2 pt-1">
                <span className="text-xs font-code-sm text-outline font-semibold uppercase">
                  Suggested queries matching current graph data:
                </span>
                <div className="flex flex-wrap gap-2">
                  <button
                    onClick={() => {
                      const q = 'Why did we choose PostgreSQL for the Payment Service?';
                      setQuestion(q);
                      handleAsk(q);
                    }}
                    className="px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-xs font-medium text-secondary border border-secondary/20 transition-all cursor-pointer"
                  >
                    Why PostgreSQL for Payments?
                  </button>
                  <button
                    onClick={() => {
                      const q = 'Who owns Payment Service?';
                      setQuestion(q);
                      handleAsk(q);
                    }}
                    className="px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-xs font-medium text-secondary border border-secondary/20 transition-all cursor-pointer"
                  >
                    Who owns Payment Service?
                  </button>
                </div>
              </div>
            </section>
          )}
        </div>
      )}
    </div>
  );
};
