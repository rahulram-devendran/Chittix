import React, { useState, useEffect } from 'react';
import { askQuestion, type AskResponse } from '../services/api';

interface AskInvestigateProps {
  initialQuestion?: string;
  onSelectEvidence?: (sourceId: string) => void;
}

const SAMPLE_QUESTIONS = [
  'Why did we choose PostgreSQL for the Payment Service?',
  'Who owns Payment Service?',
  'What is our vacation policy?',
];

export const AskInvestigate: React.FC<AskInvestigateProps> = ({
  initialQuestion,
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
    } catch (err: any) {
      setError(err?.message || 'Failed to query the graph backend.');
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
  }, [initialQuestion, handleAsk]);


  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault();
    handleAsk();
  };

  return (
    <div className="flex flex-col w-full px-gutter-mobile md:px-gutter max-w-5xl mx-auto space-y-space-md pb-24 pt-4 text-on-surface">
      {/* Query & Controls Section */}
      <div className="flex flex-col gap-space-sm bg-surface-container rounded-xl p-space-md border border-outline-variant/20 shadow-md">
        <div className="flex items-center justify-between">
          <div className="flex items-center gap-1.5">
            <span className="w-2 h-2 rounded-full bg-primary-container animate-pulse" />
            <span className="font-code-sm text-code-sm text-on-surface-variant uppercase tracking-wider font-semibold">
              Investigative Prompt // Active Query
            </span>
          </div>
          <span className="font-code-sm text-code-sm px-1.5 py-0.5 rounded bg-surface-container-high text-primary-container">
            Source: FalkorDB
          </span>
        </div>

        {/* Input Form */}
        <form onSubmit={handleSubmit} className="flex flex-col gap-2">
          <div className="relative flex items-center">
            <span className="material-symbols-outlined text-[20px] text-primary-container absolute left-3 pointer-events-none">
              troubleshoot
            </span>
            <input
              value={question}
              onChange={e => setQuestion(e.target.value)}
              placeholder="Ask anything about systems, decisions, or projects..."
              className="w-full pl-10 pr-24 py-2.5 rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md placeholder:text-outline border border-outline-variant/30 focus:outline-none focus:border-secondary transition-all"
            />
            <button
              type="submit"
              disabled={loading || !question.trim()}
              className="absolute right-2 px-3 py-1.5 rounded-lg bg-primary-container text-on-primary-fixed font-headline-sm text-[12px] font-semibold uppercase disabled:opacity-50 active:scale-95 transition-all shadow-sm"
            >
              {loading ? 'Running...' : 'Execute'}
            </button>
          </div>
        </form>

        {/* Quick Suggestion Pills */}
        <div className="flex items-center gap-space-xs overflow-x-auto py-0.5 no-scrollbar">
          <span className="text-[11px] font-code-sm text-outline flex-shrink-0">Try:</span>
          {SAMPLE_QUESTIONS.map((sq, idx) => (
            <button
              key={idx}
              onClick={() => {
                setQuestion(sq);
                handleAsk(sq);
              }}
              className="flex-shrink-0 px-2 py-0.5 rounded bg-surface-container-high hover:bg-surface-bright text-on-surface text-label-sm font-label-sm transition-all active:scale-95 border border-outline-variant/20 truncate max-w-xs"
            >
              {sq}
            </button>
          ))}
        </div>

        {/* Latency & Telemetry */}
        <div className="flex items-center justify-between pt-1 border-t border-outline-variant/20 text-[11px] font-code-sm text-on-surface-variant">
          <div className="flex items-center gap-2">
            <span className="px-1.5 py-0.5 rounded bg-surface-container-high text-secondary">
              Cypher Graph Engine
            </span>
            <span className="text-outline">Ready for LLM backend</span>
          </div>
          {latencyMs !== null && (
            <span className="flex items-center gap-1 text-tertiary-container font-mono">
              <span className="material-symbols-outlined text-[13px]">speed</span> {latencyMs}ms
            </span>
          )}
        </div>
      </div>

      {/* Loading State */}
      {loading && (
        <div className="w-full p-12 rounded-xl bg-surface-container border border-outline-variant/20 flex flex-col items-center justify-center gap-2 text-on-surface-variant">
          <span className="material-symbols-outlined text-[32px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-code-sm">Retrieving factual graph paths from FalkorDB...</span>
        </div>
      )}

      {/* Error State */}
      {error && !loading && (
        <div className="p-space-md rounded-xl bg-error-container/20 border border-error/40 flex flex-col gap-2">
          <div className="flex items-center gap-2 text-error">
            <span className="material-symbols-outlined text-[20px]">error</span>
            <span className="font-headline-sm text-headline-sm font-semibold">
              Query Execution Failed
            </span>
          </div>
          <p className="font-body-md text-body-md text-on-surface-variant">{error}</p>
          <p className="font-body-sm text-body-sm text-outline">
            Ensure the Spring Boot backend is running on port 8080.
          </p>
          <button
            onClick={() => handleAsk()}
            className="self-start mt-1 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md transition-colors border border-outline-variant/30 flex items-center gap-1"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry Query
          </button>
        </div>
      )}

      {/* Answer Panel */}
      {response && !loading && (
        <div className="space-y-space-md">
          {/* Natural Language Synthesized Response Card */}
          <div className="bg-surface-container rounded-xl p-space-md space-y-space-sm border border-outline-variant/20 shadow-md">
            <div className="flex items-center justify-between">
              <div className="flex items-center gap-1.5">
                <span className="material-symbols-outlined text-[18px] text-primary-container">
                  psychology
                </span>
                <span className="font-label-sm text-label-sm uppercase tracking-wider text-on-surface-variant font-semibold">
                  Graph Resolution
                </span>
              </div>
              <span className="font-code-sm text-code-sm px-1.5 py-0.5 rounded bg-surface-container-highest text-tertiary-container font-mono">
                {response.answerType || (response.answered ? 'PRELIMINARY' : 'UNANSWERED')}
              </span>
            </div>

            {/* Answer Text */}
            <p className="font-body-md text-body-md text-primary font-medium leading-relaxed">
              {response.answer}
            </p>

            {/* Segmented Evidence Breakdown */}
            {response.answered ? (
              <div className="pt-space-xs space-y-space-xs">
                {/* Confirmed Graph Facts */}
                {response.facts && response.facts.length > 0 && (
                  <div className="bg-surface-container-low rounded-lg p-space-sm space-y-space-xs border border-outline-variant/20">
                    <div className="flex items-center justify-between">
                      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-tertiary-container/15 text-tertiary-container font-label-sm text-label-sm uppercase tracking-wide font-semibold">
                        <span className="material-symbols-outlined text-[13px]">verified</span> Confirmed Graph Facts
                      </span>
                      <span className="font-code-sm text-code-sm text-tertiary-container">
                        {response.facts.length} Verified
                      </span>
                    </div>
                    <ul className="space-y-1.5 pt-0.5">
                      {response.facts.map((fact, i) => (
                        <li key={i} className="flex items-start gap-2">
                          <span className="w-1.5 h-1.5 rounded-full bg-tertiary-container mt-1.5 flex-shrink-0" />
                          <p className="font-body-sm text-body-sm text-on-surface">{fact}</p>
                        </li>
                      ))}
                    </ul>
                  </div>
                )}

                {/* Grounded Decision Contexts */}
                {response.decisions && response.decisions.length > 0 && (
                  <div className="bg-surface-container-low rounded-lg p-space-sm space-y-space-xs border border-outline-variant/20">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-primary-container/20 text-primary-container font-label-sm text-label-sm uppercase tracking-wide font-semibold">
                      <span className="material-symbols-outlined text-[13px]">account_tree</span> Decision Chains
                    </span>
                    <div className="space-y-2 pt-1">
                      {response.decisions.map((dec, idx) => (
                        <div key={idx} className="p-2 rounded bg-surface-container border border-outline-variant/20 flex flex-col gap-1">
                          <div className="flex items-center justify-between">
                            <span className="font-headline-sm text-label-md text-primary font-semibold">
                              {dec.decision}
                            </span>
                            {dec.sourceId && (
                              <span className="font-code-sm text-[10px] px-1.5 py-0.2 rounded bg-surface-container-high text-secondary">
                                {dec.sourceId}
                              </span>
                            )}
                          </div>
                          <p className="font-body-sm text-[12px] text-on-surface-variant">
                            Affects <strong className="text-on-surface">{dec.project}</strong> (Owner: {dec.ownerTeam}) → Uses <strong className="text-secondary">{dec.system}</strong>
                          </p>
                          <div className="font-code-sm text-[10px] text-outline">
                            Path: {dec.evidencePath?.join(' → ') || 'Decision → Project → System'}
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}

                {/* Grounding Source Documents */}
                {response.evidence && response.evidence.length > 0 && (
                  <div className="bg-surface-container-low rounded-lg p-space-sm space-y-space-xs border border-outline-variant/20">
                    <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-secondary-container/20 text-secondary font-label-sm text-label-sm uppercase tracking-wide font-semibold">
                      <span className="material-symbols-outlined text-[13px]">description</span> Evidence Documents
                    </span>
                    <div className="flex flex-wrap gap-2 pt-1">
                      {response.evidence.map((ev, idx) => (
                        <div
                          key={idx}
                          className="px-2.5 py-1.5 rounded-lg bg-surface-container border border-outline-variant/20 flex items-center gap-2"
                        >
                          <span className="material-symbols-outlined text-[16px] text-secondary">article</span>
                          <div className="flex flex-col">
                            <span className="font-body-sm text-[12px] text-primary font-medium">
                              {ev.documentTitle}
                            </span>
                            <span className="font-code-sm text-[10px] text-outline">
                              Source ID: {ev.sourceId}
                            </span>
                          </div>
                        </div>
                      ))}
                    </div>
                  </div>
                )}
              </div>
            ) : (
              /* Insufficient Evidence State Card */
              <div className="bg-surface-container-low rounded-lg p-space-sm space-y-space-xs border border-error/30 mt-2">
                <div className="flex items-center justify-between">
                  <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded bg-error-container/20 text-error font-label-sm text-label-sm uppercase tracking-wide font-semibold">
                    <span className="material-symbols-outlined text-[13px]">warning</span> Insufficient Evidence State
                  </span>
                  <span className="font-code-sm text-code-sm text-error">Unresolved In Graph</span>
                </div>
                <div className="flex items-start gap-2 pt-1">
                  <span className="w-1.5 h-1.5 rounded-full bg-error mt-1.5 flex-shrink-0" />
                  <p className="font-body-sm text-body-sm text-on-surface-variant">
                    {response.answer || 'No matching decisions, projects, or systems were found for this query in the current graph.'}
                  </p>
                </div>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
