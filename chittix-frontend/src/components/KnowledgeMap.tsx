import React, { useState } from 'react';
import type { PaymentsContextResponse, DecisionContext } from '../services/api';

interface KnowledgeMapProps {
  paymentsContext: PaymentsContextResponse | null;
  loading: boolean;
  onRefresh: () => void;
  onNavigateToAsk: (query: string) => void;
}

type NodeCategory = 'all' | 'projects' | 'systems' | 'decisions' | 'docs';

interface GraphNodeData {
  id: string;
  name: string;
  type: 'Project' | 'System' | 'Decision' | 'Document' | 'Team';
  subText: string;
  badge: string;
  properties: Record<string, string>;
  incoming: { source: string; edge: string; type: string }[];
  outgoing: { target: string; edge: string; type: string }[];
  evidenceDoc?: string;
  sourceId?: string;
}

export const KnowledgeMap: React.FC<KnowledgeMapProps> = ({
  paymentsContext,
  loading,
  onRefresh,
  onNavigateToAsk,
}) => {
  const [filterQuery, setFilterQuery] = useState('');
  const [category, setCategory] = useState<NodeCategory>('all');
  const [selectedNodeId, setSelectedNodeId] = useState<string>('project');

  const contexts = paymentsContext?.contexts || [];
  const primary: DecisionContext | undefined = contexts[0];

  // Build actual graph nodes directly from real backend decision contexts
  const nodes: Record<string, GraphNodeData> = primary
    ? {
        project: {
          id: 'project',
          name: primary.project,
          type: 'Project',
          subText: 'Core Enterprise Microservice',
          badge: 'PRODUCTION TIER-1',
          properties: {
            OwnerTeam: primary.ownerTeam,
            TargetSystem: primary.system,
            AssociatedDecision: primary.decision,
            EvidencePath: primary.evidencePath.join(' → '),
          },
          incoming: [
            { source: primary.ownerTeam, edge: ':OWNS', type: 'Team' },
            { source: primary.decision, edge: ':AFFECTS', type: 'Decision' },
          ],
          outgoing: [
            { target: primary.system, edge: ':USES', type: 'System' },
          ],
          evidenceDoc: primary.documentTitle,
          sourceId: primary.sourceId,
        },
        system: {
          id: 'system',
          name: primary.system,
          type: 'System',
          subText: 'Primary Database & Persistence',
          badge: 'MANAGED SYSTEM',
          properties: {
            SystemType: 'Relational Database Engine',
            UsedBy: primary.project,
            DecisionRationale: primary.decision,
          },
          incoming: [
            { source: primary.project, edge: ':USES', type: 'Project' },
            { source: primary.decision, edge: ':RESOLVED_BY', type: 'Decision' },
          ],
          outgoing: [],
          evidenceDoc: primary.documentTitle,
          sourceId: primary.sourceId,
        },
        decision: {
          id: 'decision',
          name: primary.decision,
          type: 'Decision',
          subText: 'Architectural Decision Record',
          badge: 'VERIFIED ADR',
          properties: {
            DocumentTitle: primary.documentTitle,
            SourceId: primary.sourceId,
            TargetProject: primary.project,
            ResolvedSystem: primary.system,
          },
          incoming: [
            { source: primary.documentTitle, edge: ':SUPPORTED_BY', type: 'Document' },
          ],
          outgoing: [
            { target: primary.project, edge: ':AFFECTS', type: 'Project' },
            { target: primary.system, edge: ':RESOLVED_BY', type: 'System' },
          ],
          evidenceDoc: primary.documentTitle,
          sourceId: primary.sourceId,
        },
        document: {
          id: 'document',
          name: primary.documentTitle,
          type: 'Document',
          subText: 'Source of Truth RFC / Spec',
          badge: 'EVIDENCE RECORD',
          properties: {
            SourceId: primary.sourceId,
            Validates: primary.decision,
          },
          incoming: [],
          outgoing: [
            { target: primary.decision, edge: ':SUPPORTED_BY', type: 'Decision' },
          ],
          evidenceDoc: primary.documentTitle,
          sourceId: primary.sourceId,
        },
        team: {
          id: 'team',
          name: primary.ownerTeam,
          type: 'Team',
          subText: 'Domain Engineering Team',
          badge: 'OWNERSHIP SQUAD',
          properties: {
            OwnsProject: primary.project,
          },
          incoming: [],
          outgoing: [
            { target: primary.project, edge: ':OWNS', type: 'Project' },
          ],
        },
      }
    : {};

  const activeNode = nodes[selectedNodeId] || nodes['project'] || null;

  const [zoomLevel, setZoomLevel] = useState<number>(1);
  const [highlightPath, setHighlightPath] = useState<boolean>(true);

  // Navigate to connected node when clicking an edge in the inspector
  const handleSelectConnected = (nodeName: string) => {
    const matchedKey = Object.keys(nodes).find(
      key => nodes[key].name.toLowerCase() === nodeName.toLowerCase() ||
             nodes[key].id.toLowerCase() === nodeName.toLowerCase()
    );
    if (matchedKey) {
      setSelectedNodeId(matchedKey);
    }
  };
  const filteredNodeKeys = Object.keys(nodes).filter(key => {
    const node = nodes[key];
    if (category === 'projects' && node.type !== 'Project') return false;
    if (category === 'systems' && node.type !== 'System') return false;
    if (category === 'decisions' && node.type !== 'Decision') return false;
    if (category === 'docs' && node.type !== 'Document') return false;
    if (filterQuery) {
      const q = filterQuery.toLowerCase();
      return (
        node.name.toLowerCase().includes(q) ||
        node.type.toLowerCase().includes(q) ||
        node.subText.toLowerCase().includes(q)
      );
    }
    return true;
  });

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      {/* Header and Filter Bar */}
      <section className="flex flex-col gap-3 bg-surface-container rounded-2xl p-5 md:p-6 border border-outline-variant/30 shadow-sm transition-colors">
        <div className="relative w-full flex items-center">
          <span className="material-symbols-outlined text-[20px] text-on-surface-variant absolute left-3.5 pointer-events-none">
            search
          </span>
          <input
            value={filterQuery}
            onChange={e => setFilterQuery(e.target.value)}
            className="w-full h-11 pl-11 pr-10 bg-surface-container-lowest text-on-surface font-body-sm text-sm rounded-xl placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:ring-2 focus:ring-secondary/40 focus:border-secondary transition-all"
            placeholder="Search nodes (Project, System, Decision, Document, Team)..."
            type="text"
          />
          {filterQuery && (
            <button
              onClick={() => setFilterQuery('')}
              className="absolute right-3.5 text-outline hover:text-primary cursor-pointer"
            >
              <span className="material-symbols-outlined text-[18px]">close</span>
            </button>
          )}
        </div>

        {/* Category Filter Pills */}
        <div className="flex items-center gap-2 overflow-x-auto pb-1 no-scrollbar">
          <button
            onClick={() => setCategory('all')}
            className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
              category === 'all'
                ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
            }`}
          >
            All Entities ({Object.keys(nodes).length})
          </button>
          <button
            onClick={() => setCategory('projects')}
            className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
              category === 'projects'
                ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
            }`}
          >
            Projects
          </button>
          <button
            onClick={() => setCategory('systems')}
            className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
              category === 'systems'
                ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
            }`}
          >
            Systems
          </button>
          <button
            onClick={() => setCategory('decisions')}
            className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
              category === 'decisions'
                ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
            }`}
          >
            Decisions
          </button>
          <button
            onClick={() => setCategory('docs')}
            className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
              category === 'docs'
                ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
            }`}
          >
            Evidence Docs
          </button>
        </div>

        {/* Quick Matching Chips */}
        {filteredNodeKeys.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pt-1 no-scrollbar text-xs font-code-sm border-t border-outline-variant/20">
            <span className="text-outline flex-shrink-0 font-medium">Select:</span>
            {filteredNodeKeys.map(key => (
              <button
                key={key}
                onClick={() => setSelectedNodeId(key)}
                className={`px-2.5 py-1 rounded-md border transition-all flex-shrink-0 cursor-pointer ${
                  selectedNodeId === key
                    ? 'bg-secondary-container text-on-secondary-container border-secondary font-bold shadow-sm'
                    : 'bg-surface-container-high border-outline-variant/30 text-on-surface-variant hover:text-primary hover:border-outline-variant'
                }`}
              >
                {nodes[key]?.name}
              </button>
            ))}
          </div>
        )}
      </section>

      {/* Main Knowledge Map Canvas */}
      {loading ? (
        <div className="w-full h-96 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center gap-3 text-on-surface-variant shadow-sm">
          <span className="material-symbols-outlined text-[36px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-xs">Traversing FalkorDB Subgraph...</span>
        </div>
      ) : !primary ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center text-center gap-3 shadow-sm">
          <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[28px]">hub</span>
          </div>
          <span className="font-headline-sm text-base text-primary font-bold">
            Graph Data Unavailable
          </span>
          <p className="font-body-sm text-xs text-on-surface-variant max-w-md">
            Start the Spring Boot backend (`mvnw.cmd spring-boot:run`) to stream verified graph relationships from FalkorDB.
          </p>
          <button
            onClick={onRefresh}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/30 flex items-center gap-1.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Refresh Subgraph
          </button>
        </div>
      ) : (
        <div className="flex flex-col rounded-2xl overflow-hidden border border-outline-variant/30 bg-surface-container shadow-sm transition-colors">
          {/* Responsive Unified SVG Canvas */}
          <div className="relative w-full h-[400px] overflow-hidden select-none bg-surface-container-lowest">
            {/* Viewport Floating Controls HUD (Stitch Approved Asset) */}
            <div className="absolute top-3 right-3 flex flex-col gap-1.5 bg-surface-container-high/90 backdrop-blur-md p-1 rounded-xl shadow-lg z-20 border border-outline-variant/30">
              <button
                onClick={() => setZoomLevel(prev => Math.min(prev + 0.15, 1.6))}
                aria-label="Zoom in"
                title="Zoom in graph"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">add</span>
              </button>
              <button
                onClick={() => setZoomLevel(prev => Math.max(prev - 0.15, 0.7))}
                aria-label="Zoom out"
                title="Zoom out graph"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">remove</span>
              </button>
              <button
                onClick={() => setZoomLevel(1)}
                aria-label="Fit View"
                title="Reset zoom and fit view"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px]">fit_screen</span>
              </button>
              <button
                onClick={() => setHighlightPath(prev => !prev)}
                aria-label="Path Mode"
                title="Toggle verified path highlights"
                className={`w-8 h-8 flex items-center justify-center rounded-lg transition-colors cursor-pointer ${
                  highlightPath
                    ? 'bg-primary-container text-on-primary-container shadow-sm'
                    : 'bg-surface-container text-on-surface hover:bg-surface-bright'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">route</span>
              </button>
            </div>

            <svg
              className="w-full h-full"
              viewBox="0 0 460 340"
              preserveAspectRatio="xMidYMid meet"
            >
              <defs>
                <marker id="kmap-arrow-cyan" markerWidth="8" markerHeight="8" refX="17" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-cyan)" />
                </marker>
                <marker id="kmap-arrow-lime" markerWidth="8" markerHeight="8" refX="19" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-lime)" />
                </marker>

                <pattern id="kmap-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                  <circle cx="2" cy="2" r="0.75" fill="var(--color-outline)" opacity="0.3" />
                </pattern>
              </defs>

              <rect width="100%" height="100%" fill="url(#kmap-grid)" />

              <g style={{ transform: `scale(${zoomLevel})`, transformOrigin: '230px 170px', transition: 'transform 0.2s ease-out' }}>
                {/* Verified Relationship Edges */}
                <g className="relationships">
                  {/* Document -> Decision */}
                  <line
                    x1="90" y1="90" x2="220" y2="90"
                    stroke="var(--graph-edge-cyan)"
                    strokeWidth={highlightPath ? '2.5' : '1.5'}
                    strokeDasharray="4 3"
                    markerEnd="url(#kmap-arrow-cyan)"
                  />
                  <rect x="115" y="76" width="80" height="16" rx="4" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.8" />
                  <text x="155" y="88" fill="var(--graph-edge-cyan)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :SUPPORTED_BY
                  </text>

                  {/* Decision -> Project */}
                  <line
                    x1="220" y1="90" x2="220" y2="210"
                    stroke="var(--graph-edge-lime)"
                    strokeWidth={highlightPath ? '3' : '2'}
                    markerEnd="url(#kmap-arrow-lime)"
                  />
                  <rect x="185" y="142" width="70" height="16" rx="4" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.8" />
                  <text x="220" y="154" fill="var(--graph-edge-lime)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :AFFECTS
                  </text>

                  {/* Team -> Project */}
                  <line
                    x1="90" y1="210" x2="220" y2="210"
                    stroke="var(--graph-edge-lime)"
                    strokeWidth="2"
                    strokeDasharray="4 3"
                    markerEnd="url(#kmap-arrow-lime)"
                  />
                  <rect x="125" y="198" width="60" height="16" rx="4" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.8" />
                  <text x="155" y="210" fill="var(--graph-edge-lime)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :OWNS
                  </text>

                  {/* Project -> System */}
                  <line
                    x1="220" y1="210" x2="360" y2="210"
                    stroke="var(--graph-edge-cyan)"
                    strokeWidth={highlightPath ? '3' : '2'}
                    markerEnd="url(#kmap-arrow-cyan)"
                  />
                  <rect x="260" y="198" width="60" height="16" rx="4" fill="#1e2025" stroke="var(--graph-node-border)" strokeWidth="0.8" />
                  <text x="290" y="210" fill="var(--graph-edge-cyan)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                    :USES
                  </text>
                </g>

                {/* Interactive Node Elements */}
                <g className="nodes">
                  {/* Document Node */}
                  <g
                    onClick={() => setSelectedNodeId('document')}
                    transform="translate(90, 90)"
                    className="cursor-pointer group"
                  >
                    <title>{primary.documentTitle} ({primary.sourceId})</title>
                    <circle
                      r={selectedNodeId === 'document' ? '28' : '24'}
                      fill="var(--graph-node-bg)"
                      stroke="var(--graph-edge-emerald)"
                      strokeWidth={selectedNodeId === 'document' ? '3.5' : '2'}
                      className="transition-all"
                    />
                    <text dy="-3" fill="var(--graph-edge-emerald)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      DOC
                    </text>
                    <text dy="8" fill="var(--graph-text)" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      {primary.sourceId}
                    </text>
                    <text dy="38" fill="var(--graph-text)" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Evidence Spec
                    </text>
                  </g>

                  {/* Decision Node */}
                  <g
                    onClick={() => setSelectedNodeId('decision')}
                    transform="translate(220, 90)"
                    className="cursor-pointer group"
                  >
                    <title>{primary.decision}</title>
                    <circle
                      r={selectedNodeId === 'decision' ? '30' : '26'}
                      fill="var(--graph-node-bg)"
                      stroke="var(--graph-edge-cyan)"
                      strokeWidth={selectedNodeId === 'decision' ? '4' : '2.5'}
                      className="transition-all"
                    />
                    <text dy="-3" fill="var(--graph-edge-cyan)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      DECISION
                    </text>
                    <text dy="8" fill="var(--graph-text)" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Architecture
                    </text>
                    <text dy="38" fill="var(--graph-text)" fontSize="8" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      PgSQL ADR
                    </text>
                  </g>

                  {/* Team Node */}
                  <g
                    onClick={() => setSelectedNodeId('team')}
                    transform="translate(90, 210)"
                    className="cursor-pointer group"
                  >
                    <title>{primary.ownerTeam} Team (Maintenance Squad)</title>
                    <circle
                      r={selectedNodeId === 'team' ? '28' : '24'}
                      fill="var(--graph-node-bg)"
                      stroke="var(--graph-edge-lime)"
                      strokeWidth={selectedNodeId === 'team' ? '3.5' : '2'}
                      className="transition-all"
                    />
                    <text dy="-3" fill="var(--graph-edge-lime)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      TEAM
                    </text>
                    <text dy="8" fill="var(--graph-text)" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Owner
                    </text>
                    <text dy="38" fill="var(--graph-text)" fontSize="8.5" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      {primary.ownerTeam}
                    </text>
                  </g>

                  {/* Project Node (Core Hub) */}
                  <g
                    onClick={() => setSelectedNodeId('project')}
                    transform="translate(220, 210)"
                    className="cursor-pointer group"
                  >
                    <title>{primary.project} (Tier-1 Service)</title>
                    <circle
                      r={selectedNodeId === 'project' ? '36' : '32'}
                      fill="var(--graph-node-bg)"
                      stroke="var(--graph-edge-lime)"
                      strokeWidth={selectedNodeId === 'project' ? '4.5' : '3'}
                      className="transition-all"
                    />
                    <text dy="-4" fill="var(--graph-edge-lime)" fontSize="10.5" fontFamily="Geist" fontWeight="900" textAnchor="middle">
                      PROJECT
                    </text>
                    <text dy="9" fill="var(--graph-text)" fontSize="7.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      Tier-1 Service
                    </text>
                    <text dy="44" fill="var(--graph-text)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      Pay-Svc
                    </text>
                  </g>

                  {/* System Node */}
                  <g
                    onClick={() => setSelectedNodeId('system')}
                    transform="translate(360, 210)"
                    className="cursor-pointer group"
                  >
                    <title>{primary.system} (Managed Database)</title>
                    <circle
                      r={selectedNodeId === 'system' ? '30' : '26'}
                      fill="var(--graph-node-bg)"
                      stroke="var(--graph-edge-cyan)"
                      strokeWidth={selectedNodeId === 'system' ? '4' : '2.5'}
                      className="transition-all"
                    />
                    <text dy="-3" fill="var(--graph-edge-cyan)" fontSize="9" fontFamily="Geist" fontWeight="800" textAnchor="middle">
                      SYSTEM
                    </text>
                    <text dy="8" fill="var(--graph-text)" fontSize="7" fontFamily="Geist" fontWeight="600" textAnchor="middle">
                      Database
                    </text>
                    <text dy="38" fill="var(--graph-text)" fontSize="8.5" fontFamily="Geist" fontWeight="700" textAnchor="middle">
                      {primary.system}
                    </text>
                  </g>
                </g>
              </g>
            </svg>

            {/* Active Path Breadcrumb Pill - Responsive and Wrap-Safe */}
            <div className="absolute bottom-3 left-4 max-w-[calc(100%-2rem)] flex items-center gap-2 bg-surface/90 backdrop-blur-md px-3 py-1.5 rounded-full shadow-md z-20 border border-outline-variant/30 truncate">
              <span className="w-2 h-2 rounded-full bg-secondary animate-pulse flex-shrink-0" />
              <span className="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider font-semibold flex-shrink-0">
                Verified Path:
              </span>
              <span className="font-code-sm text-xs text-primary font-bold truncate">
                {primary.decision} → {primary.project} → {primary.system}
              </span>
            </div>
          </div>

          {/* Bottom Entity Inspector Sheet */}
          {activeNode && (
            <div className="w-full bg-surface-container border-t border-outline-variant/30 flex flex-col p-5 md:p-6 gap-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2.5">
                    <span className="font-headline-sm text-xl font-bold text-primary truncate">
                      {activeNode.name}
                    </span>
                    <span className="font-code-sm text-xs uppercase px-2.5 py-0.5 rounded-full bg-primary-container text-on-primary-container font-bold">
                      {activeNode.type}
                    </span>
                  </div>
                  <div className="flex items-center gap-2 mt-1">
                    <span className="font-label-md text-xs text-secondary font-semibold">
                      {activeNode.subText}
                    </span>
                    <span className="text-outline text-xs">•</span>
                    <span className="font-code-sm text-xs text-tertiary font-bold">
                      {activeNode.badge}
                    </span>
                  </div>
                </div>

                <button
                  onClick={() => onNavigateToAsk(`What is the relationship between ${activeNode.name} and other entities?`)}
                  className="self-start sm:self-center px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary text-xs font-semibold flex items-center gap-1.5 transition-colors border border-outline-variant/30 cursor-pointer shadow-sm"
                >
                  <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                  <span>Investigate In Graph</span>
                </button>
              </div>

              {/* Known Technical Properties */}
              <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col gap-2">
                <span className="font-label-sm text-xs text-outline uppercase tracking-wider font-bold">
                  Verified Technical Properties (FalkorDB)
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3 mt-1">
                  {Object.entries(activeNode.properties).map(([key, val]) => (
                    <div key={key} className="flex flex-col">
                      <span className="font-body-sm text-xs text-on-surface-variant font-medium">{key}</span>
                      <span className="font-code-sm text-xs text-primary font-semibold break-all">
                        {val}
                      </span>
                    </div>
                  ))}
                </div>
                <div className="pt-1 text-[11px] font-code-sm text-outline italic">
                  Note: Graph facts verify structural entity topology. Written approval logs or sign-off dates are not cataloged in the database.
                </div>
              </div>

              {/* Graph Topology: Incoming & Outgoing (Clickable to Explore!) */}
              <div className="flex flex-col gap-3">
                <div className="flex items-center justify-between">
                  <span className="font-headline-sm text-sm text-primary font-bold uppercase tracking-wider">
                    Connected Relationships (Click to Navigate)
                  </span>
                  <span className="font-code-sm text-xs text-on-surface-variant font-medium">
                    {activeNode.incoming.length + activeNode.outgoing.length} Active Edges
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                  {/* Incoming */}
                  <div className="flex flex-col gap-2">
                    <span className="text-on-surface-variant font-label-sm text-xs uppercase tracking-wider font-semibold flex items-center gap-1">
                      <span className="material-symbols-outlined text-[15px]">arrow_downward</span>
                      Incoming ({activeNode.incoming.length})
                    </span>
                    {activeNode.incoming.length === 0 ? (
                      <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/20 text-xs text-outline italic">
                        Root entity in current chain
                      </div>
                    ) : (
                      activeNode.incoming.map((inc, i) => (
                        <button
                          key={i}
                          onClick={() => handleSelectConnected(inc.source)}
                          title={`Navigate to ${inc.source}`}
                          className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/20 hover:border-outline-variant transition-colors cursor-pointer text-left"
                        >
                          <span className="font-body-md text-xs text-on-surface font-semibold truncate">
                            {inc.source}
                          </span>
                          <span className="font-code-sm text-xs px-2 py-0.5 rounded bg-surface-container-high text-primary font-bold ml-2">
                            {inc.edge}
                          </span>
                        </button>
                      ))
                    )}
                  </div>

                  {/* Outgoing */}
                  <div className="flex flex-col gap-2">
                    <span className="text-on-surface-variant font-label-sm text-xs uppercase tracking-wider font-semibold flex items-center gap-1">
                      <span className="material-symbols-outlined text-[15px]">arrow_upward</span>
                      Outgoing ({activeNode.outgoing.length})
                    </span>
                    {activeNode.outgoing.length === 0 ? (
                      <div className="p-3 rounded-lg bg-surface-container-low border border-outline-variant/20 text-xs text-outline italic">
                        Terminal entity in current chain
                      </div>
                    ) : (
                      activeNode.outgoing.map((out, i) => (
                        <button
                          key={i}
                          onClick={() => handleSelectConnected(out.target)}
                          title={`Navigate to ${out.target}`}
                          className="flex items-center justify-between p-3 rounded-lg bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/20 hover:border-outline-variant transition-colors cursor-pointer text-left"
                        >
                          <span className="font-body-md text-xs text-on-surface font-semibold truncate">
                            {out.target}
                          </span>
                          <span className="font-code-sm text-xs px-2 py-0.5 rounded bg-surface-container-high text-secondary font-bold ml-2">
                            {out.edge}
                          </span>
                        </button>
                      ))
                    )}
                  </div>
                </div>
              </div>


              {/* Verified Evidence Attachment */}
              {activeNode.evidenceDoc && (
                <div className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/25 flex items-center justify-between gap-3">
                  <div className="flex items-center gap-3 min-w-0">
                    <div className="w-8 h-8 rounded-lg bg-tertiary-container/30 flex items-center justify-center flex-shrink-0 text-tertiary">
                      <span className="material-symbols-outlined text-[20px]">verified</span>
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className="font-headline-sm text-sm text-primary font-bold truncate">
                        {activeNode.evidenceDoc}
                      </span>
                      <span className="font-body-sm text-xs text-tertiary font-medium">
                        Source ID: {activeNode.sourceId || 'DOC-001'} • Ground-truth FalkorDB record
                      </span>
                    </div>
                  </div>
                </div>
              )}
            </div>
          )}
        </div>
      )}
    </div>
  );
};
