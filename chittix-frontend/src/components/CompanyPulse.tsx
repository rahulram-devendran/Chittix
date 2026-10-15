import React, { useState } from 'react';
import type { HealthResponse, PaymentsContextResponse, DecisionContext } from '../services/api';

interface CompanyPulseProps {
  health: HealthResponse | null;
  healthLoading: boolean;
  paymentsContext: PaymentsContextResponse | null;
  loadingContext: boolean;
  onRefreshData: () => void;
  onNavigateToAsk: (query?: string) => void;
}

export const CompanyPulse: React.FC<CompanyPulseProps> = ({
  health,
  healthLoading,
  paymentsContext,
  loadingContext,
  onRefreshData,
  onNavigateToAsk,
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [selectedNode, setSelectedNode] = useState<{
    name: string;
    description: string;
    meta: string;
    type: 'Person' | 'Team' | 'Decision' | 'System' | 'Project' | 'Document';
    id: string;
  } | null>(null);

  const isGraphOnline = health?.falkordb === 'UP';
  const contexts = paymentsContext?.contexts || [];
  const primaryContext: DecisionContext | undefined = contexts[0];

  const handleQuerySubmit = (e: React.FormEvent) => {
    e.preventDefault();
    if (searchQuery.trim()) {
      onNavigateToAsk(searchQuery.trim());
    }
  };

  return (
    <div className="flex flex-col w-full px-gutter-mobile md:px-gutter max-w-5xl mx-auto space-y-space-md pb-24 pt-4">
      {/* System Telemetry Scrim / Status Bar */}
      <div className="w-full flex items-center justify-between px-space-md py-space-xs rounded-xl bg-surface-container-low border border-outline-variant/20 shadow-sm">
        <div className="flex items-center gap-space-xs min-w-0">
          <span
            className={`inline-block w-2 h-2 rounded-full flex-shrink-0 ${
              isGraphOnline ? 'bg-secondary animate-ping' : 'bg-outline'
            }`}
          />
          <span className="font-code-sm text-code-sm text-on-surface-variant font-semibold uppercase tracking-wider truncate">
            {healthLoading
              ? 'POLLING FALKORDB TELEMETRY...'
              : isGraphOnline
              ? `BACKEND: ${health?.application?.toUpperCase() || 'COMPANY-BRAIN'} • GRAPH: ${health?.graph || 'COMPANY_BRAIN'} (UP)`
              : 'BACKEND: FALKORDB (STANDBY / DISCONNECTED)'}
          </span>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <span className="font-code-sm text-code-sm text-tertiary-container font-mono tracking-tight hidden sm:inline">
            v0.1.0-mvp
          </span>
          <button
            onClick={onRefreshData}
            title="Refresh graph data"
            className="p-1 rounded text-on-surface-variant hover:text-primary transition-colors"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
          </button>
        </div>
      </div>

      {/* Hero Header Typography Block */}
      <div className="flex flex-col space-y-space-xs pt-space-xs">
        <div className="flex items-center gap-space-xs">
          <span className="px-space-xs py-0.5 rounded bg-primary-container text-on-primary-fixed font-label-sm text-label-sm uppercase tracking-wide font-semibold">
            Telemetry Layer
          </span>
          <span className="font-code-sm text-code-sm text-secondary truncate">
            ACTIVE GRAPH: {health?.graph || 'company_brain'}
          </span>
        </div>
        <h1 className="font-headline-lg-mobile md:font-headline-lg text-headline-lg-mobile md:text-headline-lg text-primary tracking-tight font-semibold">
          Your company, understood.
        </h1>
        <p className="font-body-md text-body-md text-on-surface-variant max-w-2xl">
          Explore the relationships, decisions, and dependencies that make your organization work, grounded directly in verifiable FalkorDB graph evidence.
        </p>
      </div>

      {/* Quick Investigation Input Area */}
      <div className="flex flex-col space-y-space-xs bg-surface-container rounded-xl p-space-md border border-outline-variant/20 shadow-sm">
        <form onSubmit={handleQuerySubmit} className="relative flex items-center">
          <span className="material-symbols-outlined absolute left-space-md text-on-surface-variant text-[20px] pointer-events-none">
            search_spark
          </span>
          <input
            id="pulse-search-input"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            className="w-full pl-10 pr-16 py-2.5 rounded-lg bg-surface-container-lowest text-on-surface font-body-md text-body-md placeholder:text-outline border border-outline-variant/30 focus:outline-none focus:border-secondary transition-all"
            placeholder="Ask anything about systems, decisions, or projects..."
            type="text"
          />
          <button
            type="submit"
            className="absolute right-2 px-2.5 py-1 rounded bg-primary-container text-on-primary-fixed font-label-sm text-[11px] font-semibold uppercase hover:opacity-90 active:scale-95 transition-all"
          >
            Ask
          </button>
        </form>

        {/* Quick Query Filter Pills */}
        <div className="flex items-center gap-space-xs overflow-x-auto py-1 no-scrollbar">
          <button
            onClick={() => onNavigateToAsk('Why did we choose PostgreSQL for the Payment Service?')}
            className="flex-shrink-0 px-space-sm py-1 rounded bg-surface-container-high hover:bg-surface-bright text-on-surface text-label-sm font-label-sm transition-all active:scale-95 flex items-center gap-1 border border-outline-variant/20"
          >
            <span className="material-symbols-outlined text-secondary text-[14px]">psychology</span>
            Why PostgreSQL for Payments?
          </button>
          <button
            onClick={() => onNavigateToAsk('Who owns Payment Service?')}
            className="flex-shrink-0 px-space-sm py-1 rounded bg-surface-container-high hover:bg-surface-bright text-on-surface text-label-sm font-label-sm transition-all active:scale-95 flex items-center gap-1 border border-outline-variant/20"
          >
            <span className="material-symbols-outlined text-primary-container text-[14px]">account_tree</span>
            Who owns Payment Service?
          </button>
          <button
            onClick={() => onNavigateToAsk('What is our vacation policy?')}
            className="flex-shrink-0 px-space-sm py-1 rounded bg-surface-container-high hover:bg-surface-bright text-on-surface text-label-sm font-label-sm transition-all active:scale-95 flex items-center gap-1 border border-outline-variant/20"
          >
            <span className="material-symbols-outlined text-error text-[14px]">help</span>
            What is our vacation policy?
          </button>
        </div>
      </div>

      {/* Interactive Knowledge Graph Canvas Mini-Explorer */}
      <div className="relative flex flex-col bg-surface-container-lowest rounded-xl overflow-hidden border border-outline-variant/20 shadow-md">
        {/* Graph Canvas Header Bar */}
        <div className="flex items-center justify-between px-space-md py-space-sm bg-surface-container-high border-b border-outline-variant/20">
          <div className="flex items-center gap-space-xs">
            <span className="material-symbols-outlined text-secondary text-[18px]">hub</span>
            <span className="font-headline-sm text-headline-sm text-primary font-semibold">
              Dynamic Knowledge Canvas
            </span>
          </div>
          <div className="flex items-center gap-space-xs">
            <span className="font-code-sm text-[10px] text-tertiary-container bg-surface-container px-1.5 py-0.5 rounded flex items-center gap-1 border border-tertiary-container/30">
              <span className="w-1.5 h-1.5 rounded-full bg-tertiary-container" />
              {isGraphOnline ? 'VERIFIED GRAPH' : 'STANDBY'}
            </span>
            <button
              onClick={() => {
                if (primaryContext) {
                  setSelectedNode({
                    name: primaryContext.project,
                    description: `Core project utilizing ${primaryContext.system}`,
                    meta: `Owner: ${primaryContext.ownerTeam}`,
                    type: 'Project',
                    id: primaryContext.sourceId || 'PRJ-PAY',
                  });
                }
              }}
              className="p-1 rounded bg-surface-container text-on-surface-variant hover:text-primary transition-colors"
              title="Focus Primary Node"
            >
              <span className="material-symbols-outlined text-[16px]">crop_free</span>
            </button>
          </div>
        </div>

        {/* Real Data vs Empty State */}
        {loadingContext ? (
          <div className="w-full h-72 flex flex-col items-center justify-center gap-2 text-on-surface-variant bg-surface-container-lowest">
            <span className="material-symbols-outlined text-[32px] text-secondary animate-spin">
              progress_activity
            </span>
            <span className="font-code-sm text-code-sm">Querying FalkorDB Cypher subgraphs...</span>
          </div>
        ) : contexts.length === 0 ? (
          <div className="w-full p-8 flex flex-col items-center justify-center text-center gap-3 bg-surface-container-lowest">
            <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[28px]">database_off</span>
            </div>
            <div className="flex flex-col gap-1 max-w-md">
              <span className="font-headline-sm text-headline-sm text-primary font-medium">
                No Graph Data Loaded
              </span>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                {isGraphOnline
                  ? 'FalkorDB is reachable, but no Decision/Project/System chains were returned for the demo filter.'
                  : 'Backend is currently offline or unreachable. Start the backend with .\\mvnw.cmd spring-boot:run to stream live graph entities.'}
              </p>
            </div>
            <button
              onClick={onRefreshData}
              className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md transition-colors border border-outline-variant/30 flex items-center gap-1.5"
            >
              <span className="material-symbols-outlined text-[16px]">refresh</span>
              Retry Connection
            </button>
          </div>
        ) : (
          <div>
            {/* SVG Visual Canvas for retrieved chain */}
            <div className="relative w-full h-72 bg-gradient-to-b from-surface-container-lowest via-surface-dim to-surface-container-lowest flex items-center justify-center overflow-hidden">
              <svg className="absolute inset-0 w-full h-full opacity-20 pointer-events-none" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <pattern id="pulse-grid" width="24" height="24" patternUnits="userSpaceOnUse">
                    <path d="M 24 0 L 0 0 0 24" fill="none" stroke="currentColor" strokeWidth="0.5" className="text-surface-bright" />
                  </pattern>
                </defs>
                <rect width="100%" height="100%" fill="url(#pulse-grid)" />
              </svg>

              <svg className="w-full h-full select-none" viewBox="0 0 360 280">
                <defs>
                  <marker id="pulse-arrow" markerWidth="6" markerHeight="6" refX="16" refY="5" orient="auto-start-reverse" viewBox="0 0 10 10">
                    <path d="M 0 1 L 8 5 L 0 9 z" fill="#7bd0ff" />
                  </marker>
                  <marker id="pulse-lime" markerWidth="6" markerHeight="6" refX="16" refY="5" orient="auto-start-reverse" viewBox="0 0 10 10">
                    <path d="M 0 1 L 8 5 L 0 9 z" fill="#ceee93" />
                  </marker>
                </defs>

                {/* Edges */}
                <g className="edges">
                  {/* Decision -> Project */}
                  <line x1="60" y1="80" x2="180" y2="80" stroke="#7bd0ff" strokeWidth="1.5" strokeDasharray="3,3" markerEnd="url(#pulse-arrow)" />
                  <text x="120" y="72" fill="#7bd0ff" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                    AFFECTS
                  </text>

                  {/* Project -> System */}
                  <line x1="180" y1="80" x2="300" y2="130" stroke="#ceee93" strokeWidth="1.5" strokeDasharray="3,3" markerEnd="url(#pulse-lime)" />
                  <text x="245" y="95" fill="#ceee93" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                    USES
                  </text>

                  {/* Document -> Decision */}
                  <line x1="60" y1="200" x2="60" y2="95" stroke="#7bd0ff" strokeWidth="1.5" strokeDasharray="3,3" markerEnd="url(#pulse-arrow)" />
                  <text x="75" y="150" fill="#7bd0ff" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                    SUPPORTED_BY
                  </text>

                  {/* Team -> Project */}
                  <line x1="180" y1="200" x2="180" y2="95" stroke="#ceee93" strokeWidth="1.5" strokeDasharray="3,3" markerEnd="url(#pulse-lime)" />
                  <text x="195" y="150" fill="#ceee93" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                    OWNS
                  </text>
                </g>

                {/* Nodes */}
                <g className="nodes cursor-pointer">
                  {/* Decision Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.decision,
                        description: `Architecture decision recorded in ${primaryContext.documentTitle}`,
                        meta: `Source ID: ${primaryContext.sourceId}`,
                        type: 'Decision',
                        id: primaryContext.sourceId || 'DEC-01',
                      })
                    }
                    transform="translate(60, 80)"
                  >
                    <circle r="18" fill="#1e2025" stroke="#7bd0ff" strokeWidth="2" />
                    <text dy="3.5" fill="#e2e2e9" fontSize="7.5" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Decision
                    </text>
                  </g>

                  {/* Project Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.project,
                        description: `Core service maintained by ${primaryContext.ownerTeam}`,
                        meta: `Uses: ${primaryContext.system}`,
                        type: 'Project',
                        id: 'PRJ-PAY',
                      })
                    }
                    transform="translate(180, 80)"
                  >
                    <circle r="22" fill="#1e2025" stroke="#ceee93" strokeWidth="2.5" />
                    <text dy="3.5" fill="#ceee93" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      Project
                    </text>
                  </g>

                  {/* System Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.system,
                        description: `Database system backing ${primaryContext.project}`,
                        meta: `State: Verified in graph`,
                        type: 'System',
                        id: 'SYS-DB',
                      })
                    }
                    transform="translate(300, 130)"
                  >
                    <circle r="18" fill="#1e2025" stroke="#7bd0ff" strokeWidth="2" />
                    <text dy="3.5" fill="#e2e2e9" fontSize="7.5" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      System
                    </text>
                  </g>

                  {/* Document Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.documentTitle,
                        description: `Ground-truth architectural document`,
                        meta: `Source ID: ${primaryContext.sourceId}`,
                        type: 'Document',
                        id: primaryContext.sourceId,
                      })
                    }
                    transform="translate(60, 200)"
                  >
                    <circle r="16" fill="#1e2025" stroke="#80f9c8" strokeWidth="1.5" />
                    <text dy="3.5" fill="#80f9c8" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Doc
                    </text>
                  </g>

                  {/* Team Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.ownerTeam,
                        description: `Owning organization squad`,
                        meta: `Owner of ${primaryContext.project}`,
                        type: 'Team',
                        id: 'TEAM-01',
                      })
                    }
                    transform="translate(180, 200)"
                  >
                    <circle r="16" fill="#1e2025" stroke="#ceee93" strokeWidth="1.5" />
                    <text dy="3.5" fill="#ceee93" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Team
                    </text>
                  </g>
                </g>
              </svg>
            </div>

            {/* Selected Node Inspector Panel */}
            <div id="inspector-panel" className="p-space-md bg-surface-container border-t border-outline-variant/20 flex flex-col gap-space-xs transition-colors">
              <div className="flex items-center justify-between">
                <span className="font-code-sm text-[10px] text-outline uppercase tracking-wider">
                  Inspected Node Topology
                </span>
                <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-primary font-semibold">
                  {selectedNode ? selectedNode.type : 'Select any node above'}
                </span>
              </div>
              <div className="flex items-start justify-between gap-2">
                <div className="flex flex-col">
                  <span className="font-headline-sm text-headline-sm text-primary font-semibold">
                    {selectedNode ? selectedNode.name : primaryContext.project}
                  </span>
                  <span className="font-body-sm text-body-sm text-on-surface-variant">
                    {selectedNode ? selectedNode.description : `Primary Project: ${primaryContext.project}`}
                  </span>
                  <span className="font-code-sm text-[11px] text-secondary mt-0.5">
                    {selectedNode ? selectedNode.meta : `Team: ${primaryContext.ownerTeam} • System: ${primaryContext.system}`}
                  </span>
                </div>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Verified Graph Decision Contexts List */}
      <div className="flex flex-col space-y-space-xs">
        <div className="flex items-center justify-between">
          <span className="font-headline-sm text-headline-sm text-primary font-semibold">
            Verified Decision Chains ({contexts.length})
          </span>
          <span className="font-code-sm text-[11px] text-on-surface-variant">
            FalkorDB Evidence Paths
          </span>
        </div>

        {contexts.length === 0 ? (
          <div className="p-space-md rounded-xl bg-surface-container text-center text-on-surface-variant font-body-sm border border-outline-variant/20">
            No active decision chains loaded. Ensure FalkorDB backend is running.
          </div>
        ) : (
          contexts.map((ctx, idx) => (
            <div
              key={idx}
              className="bg-surface-container rounded-xl p-space-md flex flex-col space-y-space-xs relative overflow-hidden border border-outline-variant/20 shadow-sm"
            >
              <div className="w-1.5 absolute left-0 top-0 bottom-0 bg-primary-container" />
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-space-xs">
                  <span className="px-1.5 py-0.5 rounded bg-primary-container text-on-primary-fixed font-code-sm text-[10px] font-bold tracking-wide uppercase">
                    DECISION
                  </span>
                  <span className="font-code-sm text-[11px] text-secondary">
                    {ctx.sourceId || 'RFC'}
                  </span>
                </div>
                <span className="font-code-sm text-[10px] text-tertiary-container font-mono">
                  VERIFIED
                </span>
              </div>
              <div className="font-headline-sm text-headline-sm text-on-surface font-semibold">
                {ctx.decision}
              </div>
              <p className="font-body-sm text-body-sm text-on-surface-variant">
                Affects <span className="text-primary font-medium">{ctx.project}</span> (owned by {ctx.ownerTeam}), backed by{' '}
                <span className="text-secondary font-medium">{ctx.system}</span>.
              </p>
              <div className="flex items-center gap-2 pt-1 font-code-sm text-[10px] text-outline">
                <span className="material-symbols-outlined text-[13px] text-tertiary-container">verified</span>
                <span>Source: {ctx.documentTitle}</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
