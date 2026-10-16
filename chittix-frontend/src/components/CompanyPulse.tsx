import React, { useState } from 'react';
import type {
  HealthResponse,
  PaymentsContextResponse,
  DecisionContext,
  ProviderStatusResponse,
} from '../services/api';

interface CompanyPulseProps {
  health: HealthResponse | null;
  healthLoading: boolean;
  paymentsContext: PaymentsContextResponse | null;
  loadingContext: boolean;
  providerStatus?: ProviderStatusResponse | null;
  providerStatusLoading?: boolean;
  onRefreshData: () => void;
  onNavigateToAsk: (query?: string) => void;
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
}) => {
  const [searchQuery, setSearchQuery] = useState('');
  const [showTelemetryDetails, setShowTelemetryDetails] = useState(false);
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
      {/* System Telemetry Bar */}
      <div className="w-full flex items-center justify-between px-4 py-2.5 rounded-xl bg-surface-container-low border border-outline-variant/30 shadow-sm transition-colors">
        <div className="flex items-center gap-2.5 min-w-0">
          <span className={`inline-block w-2.5 h-2.5 rounded-full flex-shrink-0 ${healthStatusColor}`} />
          <span className="font-code-sm text-xs text-on-surface-variant font-semibold uppercase tracking-wider truncate">
            {healthLoading
              ? 'POLLING FALKORDB TELEMETRY...'
              : isGraphOnline
              ? `BACKEND: ${health?.application?.toUpperCase() || 'UP'} • GRAPH: ${health?.graph || 'COMPANY_BRAIN'} (ONLINE)`
              : 'BACKEND: FALKORDB (OFFLINE / UNREACHABLE)'}
          </span>
        </div>
        <div className="flex items-center gap-2 flex-shrink-0">
          <button
            onClick={() => setShowTelemetryDetails(prev => !prev)}
            className="px-2 py-0.5 rounded bg-surface-container hover:bg-surface-container-high text-xs font-code-sm text-secondary border border-secondary/25 transition-colors cursor-pointer"
            title="Inspect Telemetry Layer"
          >
            {showTelemetryDetails ? 'Hide Telemetry' : 'Telemetry Details'}
          </button>
          <button
            onClick={onRefreshData}
            title="Refresh graph telemetry"
            className="p-1 rounded-md text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
          >
            <span className="material-symbols-outlined text-[18px]">refresh</span>
          </button>
        </div>
      </div>

      {/* Expanded Telemetry Layer Inspection Card */}
      {showTelemetryDetails && (
        <section className="bg-surface-container rounded-2xl p-5 border border-outline-variant/30 shadow-sm space-y-3 animate-fadeIn">
          <div className="flex items-center justify-between border-b border-outline-variant/20 pb-2">
            <div className="flex items-center gap-2">
              <span className="material-symbols-outlined text-secondary text-[20px]">
                monitoring
              </span>
              <span className="font-headline-sm text-sm font-bold text-primary">
                Telemetry Layer Architecture
              </span>
            </div>
            <span className="font-code-sm text-xs text-on-surface-variant">
              Live Observability
            </span>
          </div>

          <p className="font-body-sm text-xs text-on-surface-variant leading-relaxed">
            The Chittix <strong>Telemetry Layer</strong> provides real-time health verification and runtime readiness monitoring for the two core infrastructure components: the <strong>FalkorDB Cypher knowledge graph</strong> and the <strong>local LLM inference engine</strong>.
          </p>

          <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 pt-1">
            {/* FalkorDB Telemetry */}
            <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <span className="font-code-sm text-xs font-bold text-primary uppercase">
                  FalkorDB Graph Cluster
                </span>
                <span className={`inline-flex items-center gap-1 font-code-sm text-[10px] px-2 py-0.5 rounded-full font-bold ${
                  isGraphOnline
                    ? 'bg-tertiary-container/30 text-tertiary'
                    : 'bg-error-container/30 text-error'
                }`}>
                  <span className={`w-1.5 h-1.5 rounded-full ${healthStatusColor}`} />
                  {healthStatusText}
                </span>
              </div>
              <div className="font-code-sm text-xs text-on-surface-variant space-y-0.5 mt-1">
                <div>Graph Cluster: <strong className="text-secondary">{health?.graph || 'company_brain'}</strong></div>
                <div>Application: <strong className="text-primary">{health?.application || 'UNKNOWN'}</strong></div>
                <div>Status Message: <span className="text-on-surface">{health?.message || 'No response from health probe'}</span></div>
              </div>
            </div>

            {/* LLM Engine Telemetry */}
            <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col gap-1.5">
              <div className="flex items-center justify-between">
                <span className="font-code-sm text-xs font-bold text-primary uppercase">
                  LLM Inference Runtime
                </span>
                <span className={`inline-flex items-center gap-1 font-code-sm text-[10px] px-2 py-0.5 rounded-full font-bold ${
                  providerStatus?.configured
                    ? 'bg-secondary-container/30 text-secondary'
                    : 'bg-surface-container-high text-outline'
                }`}>
                  {providerStatusLoading
                    ? 'Probing...'
                    : providerStatus?.configured
                    ? 'Configured'
                    : 'Unavailable'}
                </span>
              </div>
              <div className="font-code-sm text-xs text-on-surface-variant space-y-0.5 mt-1">
                {providerStatus ? (
                  <>
                    <div>Provider: <strong className="text-primary">{providerStatus.selectedProvider}</strong></div>
                    <div>Model: <strong className="text-secondary">{providerStatus.configuredModel}</strong></div>
                    <div>Endpoint: <span className="text-on-surface font-mono">{providerStatus.baseUrl || 'Local'}</span></div>
                    <div>Capabilities: <span className="text-primary">{providerStatus.supportedCapabilities.join(', ') || 'Standard'}</span></div>
                  </>
                ) : (
                  <div className="text-outline italic">
                    LLM telemetry unavailable. Ensure backend is running at localhost:8080.
                  </div>
                )}
              </div>
            </div>
          </div>
        </section>
      )}

      {/* Hero Header Typography Block */}
      <div className="flex flex-col space-y-2 pt-1">
        <div className="flex items-center gap-2">
          <span className="px-2 py-0.5 rounded-md bg-primary-container text-on-primary-container font-code-sm text-xs uppercase tracking-wide font-bold">
            Telemetry Layer
          </span>
          <span className="font-code-sm text-xs text-secondary font-medium truncate">
            CLUSTER: {health?.graph || 'company_brain'}
          </span>
        </div>
        <h1 className="font-headline-sm text-2xl md:text-3xl text-primary tracking-tight font-bold">
          Your company, understood.
        </h1>
        <p className="font-body-md text-sm md:text-base text-on-surface-variant max-w-2xl leading-relaxed">
          Explore the relationships, architectural decisions, and system dependencies that make your organization work, grounded directly in verifiable FalkorDB graph evidence.
        </p>
      </div>

      {/* Quick Investigation Input Area */}
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
            placeholder="Ask anything about systems, decisions, or projects..."
            type="text"
          />
          <button
            type="submit"
            className="absolute right-2 px-4 py-2 rounded-lg bg-primary-container text-on-primary-container font-headline-sm text-xs font-bold uppercase tracking-wider hover:opacity-95 active:scale-95 transition-all cursor-pointer shadow-sm"
          >
            Ask
          </button>
        </form>

        {/* Quick Query Filter Pills */}
        <div className="flex items-center gap-2 overflow-x-auto py-1 no-scrollbar">
          <button
            onClick={() => onNavigateToAsk('Why did we choose PostgreSQL for the Payment Service?')}
            className="flex-shrink-0 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 flex items-center gap-1.5 border border-outline-variant/30 cursor-pointer"
          >
            <span className="material-symbols-outlined text-secondary text-[15px]">psychology</span>
            <span>Why PostgreSQL for Payments?</span>
          </button>
          <button
            onClick={() => onNavigateToAsk('Who owns Payment Service?')}
            className="flex-shrink-0 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 flex items-center gap-1.5 border border-outline-variant/30 cursor-pointer"
          >
            <span className="material-symbols-outlined text-secondary text-[15px]">account_tree</span>
            <span>Who owns Payment Service?</span>
          </button>
          <button
            onClick={() => onNavigateToAsk('What is our vacation policy?')}
            className="flex-shrink-0 px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-on-surface text-xs font-label-sm transition-all active:scale-95 flex items-center gap-1.5 border border-outline-variant/30 cursor-pointer"
          >
            <span className="material-symbols-outlined text-outline text-[15px]">help_outline</span>
            <span>What is our vacation policy?</span>
          </button>
        </div>
      </div>

      {/* Interactive Knowledge Graph Canvas Mini-Explorer */}
      <div className="relative flex flex-col bg-surface-container rounded-2xl overflow-hidden border border-outline-variant/30 shadow-sm transition-colors">
        {/* Graph Canvas Header Bar */}
        <div className="flex items-center justify-between px-5 py-3.5 bg-surface-container-high border-b border-outline-variant/30">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[20px]">hub</span>
            <span className="font-headline-sm text-sm text-primary font-bold">
              Dynamic Knowledge Canvas
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="font-code-sm text-[11px] text-tertiary bg-surface-container px-2 py-0.5 rounded-full flex items-center gap-1.5 border border-tertiary/30 font-semibold">
              <span className={`w-1.5 h-1.5 rounded-full ${healthStatusColor}`} />
              {healthLoading
                ? 'CHECKING FALKORDB'
                : isGraphOnline
                ? 'VERIFIED FALKORDB GRAPH'
                : 'FALKORDB OFFLINE'}
            </span>
            <button
              onClick={() => {
                if (primaryContext) {
                  setSelectedNode({
                    name: primaryContext.project,
                    description: `Core project utilizing ${primaryContext.system}`,
                    meta: `Owner: ${primaryContext.ownerTeam} (Maintenance Squad)`,
                    type: 'Project',
                    id: primaryContext.sourceId || 'PRJ-PAY',
                  });
                }
              }}
              className="p-1 rounded-md bg-surface-container text-on-surface-variant hover:text-primary transition-colors cursor-pointer"
              title="Focus Primary Node"
            >
              <span className="material-symbols-outlined text-[18px]">crop_free</span>
            </button>
          </div>
        </div>

        {/* Real Data vs Empty State */}
        {loadingContext ? (
          <div className="w-full h-80 flex flex-col items-center justify-center gap-3 text-on-surface-variant bg-surface-container-lowest">
            <span className="material-symbols-outlined text-[36px] text-secondary animate-spin">
              progress_activity
            </span>
            <span className="font-code-sm text-xs">Querying FalkorDB Cypher subgraphs...</span>
          </div>
        ) : contexts.length === 0 ? (
          <div className="w-full p-10 flex flex-col items-center justify-center text-center gap-3 bg-surface-container-lowest">
            <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline">
              <span className="material-symbols-outlined text-[28px]">database_off</span>
            </div>
            <div className="flex flex-col gap-1 max-w-md">
              <span className="font-headline-sm text-base text-primary font-semibold">
                No Graph Data Loaded
              </span>
              <p className="font-body-sm text-xs text-on-surface-variant">
                {isGraphOnline
                  ? 'FalkorDB is reachable, but no Decision/Project/System chains were returned for the active query.'
                  : 'Backend is currently offline or unreachable. Start the backend with .\\mvnw.cmd spring-boot:run to stream live graph entities.'}
              </p>
            </div>
            <button
              onClick={onRefreshData}
              className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/30 flex items-center gap-1.5 cursor-pointer"
            >
              <span className="material-symbols-outlined text-[16px]">refresh</span>
              Retry Connection
            </button>
          </div>
        ) : (
          <div>
            {/* SVG Visual Canvas for retrieved chain with non-overlapping labels */}
            <div className="relative w-full h-80 bg-surface-container-lowest flex items-center justify-center overflow-hidden">
              <svg className="absolute inset-0 w-full h-full opacity-15 pointer-events-none" xmlns="http://www.w3.org/2000/svg">
                <defs>
                  <pattern id="pulse-grid" width="24" height="24" patternUnits="userSpaceOnUse">
                    <path d="M 24 0 L 0 0 0 24" fill="none" stroke="currentColor" strokeWidth="0.5" className="text-outline" />
                  </pattern>
                </defs>
                <rect width="100%" height="100%" fill="url(#pulse-grid)" />
              </svg>

              <svg className="w-full h-full select-none" viewBox="0 0 400 280">
                <defs>
                  <marker id="pulse-arrow" markerWidth="7" markerHeight="7" refX="17" refY="3.5" orient="auto">
                    <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-cyan)" />
                  </marker>
                  <marker id="pulse-lime" markerWidth="7" markerHeight="7" refX="19" refY="3.5" orient="auto">
                    <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-lime)" />
                  </marker>
                </defs>

                {/* Edges with background collision protection */}
                <g className="edges">
                  {/* Decision -> Project */}
                  <line x1="80" y1="85" x2="200" y2="85" stroke="var(--graph-edge-cyan)" strokeWidth="2" strokeDasharray="4 3" markerEnd="url(#pulse-arrow)" />
                  <rect x="110" y="66" width="60" height="15" rx="3" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.5" />
                  <text x="140" y="77" fill="var(--graph-edge-cyan)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :AFFECTS
                  </text>

                  {/* Project -> System */}
                  <line x1="200" y1="85" x2="320" y2="135" stroke="var(--graph-edge-lime)" strokeWidth="2" strokeDasharray="4 3" markerEnd="url(#pulse-lime)" />
                  <rect x="238" y="96" width="50" height="15" rx="3" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.5" />
                  <text x="263" y="107" fill="var(--graph-edge-lime)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :USES
                  </text>

                  {/* Document -> Decision */}
                  <line x1="80" y1="205" x2="80" y2="110" stroke="var(--graph-edge-cyan)" strokeWidth="2" strokeDasharray="4 3" markerEnd="url(#pulse-arrow)" />
                  <rect x="35" y="145" width="90" height="15" rx="3" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.5" />
                  <text x="80" y="156" fill="var(--graph-edge-cyan)" fontSize="8" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :SUPPORTED_BY
                  </text>

                  {/* Team -> Project */}
                  <line x1="200" y1="205" x2="200" y2="114" stroke="var(--graph-edge-lime)" strokeWidth="2" strokeDasharray="4 3" markerEnd="url(#pulse-lime)" />
                  <rect x="175" y="145" width="50" height="15" rx="3" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.5" />
                  <text x="200" y="156" fill="var(--graph-edge-lime)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :OWNS
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
                        meta: `Source ID: ${primaryContext.sourceId} • Fact: Affects ${primaryContext.project}`,
                        type: 'Decision',
                        id: primaryContext.sourceId || 'DEC-01',
                      })
                    }
                    transform="translate(80, 85)"
                  >
                    <circle r="22" fill="var(--graph-node-bg)" stroke="var(--graph-edge-cyan)" strokeWidth="2.5" />
                    <text dy="3.5" fill="var(--graph-text)" fontSize="8" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      Decision
                    </text>
                  </g>

                  {/* Project Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.project,
                        description: `Core service maintained by ${primaryContext.ownerTeam}`,
                        meta: `Uses: ${primaryContext.system} • Owned by: ${primaryContext.ownerTeam}`,
                        type: 'Project',
                        id: 'PRJ-PAY',
                      })
                    }
                    transform="translate(200, 85)"
                  >
                    <circle r="26" fill="var(--graph-node-bg)" stroke="var(--graph-edge-lime)" strokeWidth="3" />
                    <text dy="4" fill="var(--graph-text)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      Project
                    </text>
                  </g>

                  {/* System Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.system,
                        description: `Database system backing ${primaryContext.project}`,
                        meta: `Fact: Used by ${primaryContext.project}`,
                        type: 'System',
                        id: 'SYS-DB',
                      })
                    }
                    transform="translate(320, 135)"
                  >
                    <circle r="22" fill="var(--graph-node-bg)" stroke="var(--graph-edge-cyan)" strokeWidth="2.5" />
                    <text dy="3.5" fill="var(--graph-text)" fontSize="8" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      System
                    </text>
                  </g>

                  {/* Document Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.documentTitle,
                        description: `Supporting architecture specification document`,
                        meta: `Source ID: ${primaryContext.sourceId} • Ground-truth record`,
                        type: 'Document',
                        id: primaryContext.sourceId,
                      })
                    }
                    transform="translate(80, 205)"
                  >
                    <circle r="20" fill="var(--graph-node-bg)" stroke="var(--graph-edge-emerald)" strokeWidth="2" />
                    <text dy="3.5" fill="var(--graph-text)" fontSize="7.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      Doc
                    </text>
                  </g>

                  {/* Team Node */}
                  <g
                    onClick={() =>
                      setSelectedNode({
                        name: primaryContext.ownerTeam,
                        description: `Owning maintenance engineering squad`,
                        meta: `Owner of ${primaryContext.project} (Note: Ownership records squad, not decision approval)`,
                        type: 'Team',
                        id: 'TEAM-01',
                      })
                    }
                    transform="translate(200, 205)"
                  >
                    <circle r="20" fill="var(--graph-node-bg)" stroke="var(--graph-edge-lime)" strokeWidth="2" />
                    <text dy="3.5" fill="var(--graph-text)" fontSize="8" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      Team
                    </text>
                  </g>
                </g>
              </svg>
            </div>

            {/* Selected Node Inspector Panel */}
            <div id="inspector-panel" className="p-5 bg-surface-container border-t border-outline-variant/30 flex flex-col gap-3 transition-colors">
              <div className="flex items-center justify-between">
                <span className="font-code-sm text-xs text-outline uppercase tracking-wider font-semibold">
                  Inspected Node Topology
                </span>
                <span className="font-code-sm text-xs px-2 py-0.5 rounded-md bg-surface-container-high text-primary font-bold border border-outline-variant/30">
                  {selectedNode ? selectedNode.type : 'Project (Selected)'}
                </span>
              </div>
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex flex-col min-w-0">
                  <span className="font-headline-sm text-base text-primary font-bold truncate">
                    {selectedNode ? selectedNode.name : primaryContext.project}
                  </span>
                  <span className="font-body-sm text-xs text-on-surface-variant mt-0.5">
                    {selectedNode ? selectedNode.description : `Primary Project: ${primaryContext.project}`}
                  </span>
                  <span className="font-code-sm text-xs text-secondary font-medium mt-1">
                    {selectedNode ? selectedNode.meta : `Team: ${primaryContext.ownerTeam} (Owner) • System: ${primaryContext.system}`}
                  </span>
                </div>
                <button
                  onClick={() => onNavigateToAsk(`What is the relationship between ${selectedNode ? selectedNode.name : primaryContext.project} and other entities?`)}
                  className="self-start sm:self-center px-3 py-1.5 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold flex items-center gap-1.5 transition-colors border border-outline-variant/30 cursor-pointer"
                >
                  <span className="material-symbols-outlined text-[15px]">troubleshoot</span>
                  <span>Investigate Node</span>
                </button>
              </div>
            </div>
          </div>
        )}
      </div>

      {/* Verified Graph Decision Contexts List */}
      <div className="flex flex-col space-y-3">
        <div className="flex items-center justify-between">
          <span className="font-headline-sm text-base text-primary font-bold">
            Verified Decision Chains ({contexts.length})
          </span>
          <span className="font-code-sm text-xs text-on-surface-variant font-medium">
            FalkorDB Evidence Paths
          </span>
        </div>

        {contexts.length === 0 ? (
          <div className="p-6 rounded-2xl bg-surface-container text-center text-on-surface-variant font-body-sm text-sm border border-outline-variant/30">
            No active decision chains loaded. Ensure FalkorDB backend is running.
          </div>
        ) : (
          contexts.map((ctx, idx) => (
            <div
              key={idx}
              className="bg-surface-container rounded-2xl p-5 md:p-6 flex flex-col space-y-3 relative overflow-hidden border border-outline-variant/30 shadow-sm"
            >
              <div className="w-1.5 absolute left-0 top-0 bottom-0 bg-secondary" />
              <div className="flex items-center justify-between">
                <div className="flex items-center gap-2">
                  <span className="px-2 py-0.5 rounded-md bg-secondary-container text-on-secondary-container font-code-sm text-xs font-bold tracking-wide uppercase">
                    DECISION
                  </span>
                  <span className="font-code-sm text-xs text-secondary font-medium">
                    {ctx.sourceId || 'DOC-001'}
                  </span>
                </div>
                <span className="inline-flex items-center gap-1 font-code-sm text-xs text-tertiary font-bold">
                  <span className="material-symbols-outlined text-[14px]">verified</span>
                  VERIFIED IN GRAPH
                </span>
              </div>
              <div className="font-headline-sm text-lg text-primary font-bold">
                {ctx.decision}
              </div>
              <p className="font-body-sm text-sm text-on-surface-variant leading-relaxed">
                Affects <strong className="text-primary font-semibold">{ctx.project}</strong> (owned by squad <span className="text-on-surface font-medium">{ctx.ownerTeam}</span>), backed by{' '}
                <strong className="text-secondary font-semibold">{ctx.system}</strong>. Documented in spec <span className="text-on-surface font-medium">{ctx.documentTitle}</span>.
              </p>
              <div className="flex flex-wrap items-center gap-2 pt-2 border-t border-outline-variant/20 font-code-sm text-xs text-outline">
                <span className="text-tertiary font-medium">Source Document:</span>
                <span className="text-on-surface font-medium">{ctx.documentTitle}</span>
                <span>•</span>
                <span>ID: {ctx.sourceId}</span>
                <span>•</span>
                <span className="italic text-outline">Note: Ownership reflects maintenance squad, not decision approval.</span>
              </div>
            </div>
          ))
        )}
      </div>
    </div>
  );
};
