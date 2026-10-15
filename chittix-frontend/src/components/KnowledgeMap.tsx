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

  // Filter nodes according to search and category
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
    <div className="flex flex-col w-full px-gutter-mobile md:px-gutter max-w-5xl mx-auto space-y-space-md pb-24 pt-4">
      {/* Header and Filter Bar */}
      <div className="flex flex-col gap-space-sm bg-surface-container-low p-space-md rounded-xl border border-outline-variant/20 shadow-sm">
        <div className="relative w-full flex items-center">
          <span className="material-symbols-outlined text-[18px] text-on-surface-variant absolute left-3 pointer-events-none">
            search
          </span>
          <input
            value={filterQuery}
            onChange={e => setFilterQuery(e.target.value)}
            className="w-full h-10 pl-9 pr-8 bg-surface text-on-surface font-body-sm text-body-sm rounded-lg placeholder-outline border border-outline-variant/30 focus:outline-none focus:border-secondary transition-colors"
            placeholder="Search nodes (Project, System, Decision, Document, Team)..."
            type="text"
          />
          {filterQuery && (
            <button
              onClick={() => setFilterQuery('')}
              className="absolute right-3 text-outline hover:text-primary"
            >
              <span className="material-symbols-outlined text-[16px]">close</span>
            </button>
          )}
        </div>

        <div className="flex items-center gap-space-xs overflow-x-auto pb-1 no-scrollbar">
          <button
            onClick={() => setCategory('all')}
            className={`px-2.5 py-1 rounded font-label-sm text-label-sm whitespace-nowrap transition-colors ${
              category === 'all'
                ? 'bg-primary-container text-on-primary-fixed font-semibold'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
            }`}
          >
            All Nodes ({Object.keys(nodes).length})
          </button>
          <button
            onClick={() => setCategory('projects')}
            className={`px-2.5 py-1 rounded font-label-sm text-label-sm whitespace-nowrap transition-colors ${
              category === 'projects'
                ? 'bg-primary-container text-on-primary-fixed font-semibold'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Projects
          </button>
          <button
            onClick={() => setCategory('systems')}
            className={`px-2.5 py-1 rounded font-label-sm text-label-sm whitespace-nowrap transition-colors ${
              category === 'systems'
                ? 'bg-primary-container text-on-primary-fixed font-semibold'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Systems
          </button>
          <button
            onClick={() => setCategory('decisions')}
            className={`px-2.5 py-1 rounded font-label-sm text-label-sm whitespace-nowrap transition-colors ${
              category === 'decisions'
                ? 'bg-primary-container text-on-primary-fixed font-semibold'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Decisions
          </button>
          <button
            onClick={() => setCategory('docs')}
            className={`px-2.5 py-1 rounded font-label-sm text-label-sm whitespace-nowrap transition-colors ${
              category === 'docs'
                ? 'bg-primary-container text-on-primary-fixed font-semibold'
                : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface'
            }`}
          >
            Docs
          </button>
        </div>

        {filteredNodeKeys.length > 0 && (
          <div className="flex items-center gap-1.5 overflow-x-auto pt-1 no-scrollbar text-[11px] font-code-sm">
            <span className="text-outline flex-shrink-0">Matching:</span>
            {filteredNodeKeys.map(key => (
              <button
                key={key}
                onClick={() => setSelectedNodeId(key)}
                className={`px-2 py-0.5 rounded border transition-all flex-shrink-0 ${
                  selectedNodeId === key
                    ? 'bg-secondary/20 border-secondary text-secondary font-semibold'
                    : 'bg-surface-container border-outline-variant/30 text-on-surface-variant hover:text-on-surface'
                }`}
              >
                {nodes[key]?.name}
              </button>
            ))}
          </div>
        )}
      </div>

      {/* Main Knowledge Map Canvas */}
      {loading ? (
        <div className="w-full h-96 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 flex flex-col items-center justify-center gap-2 text-on-surface-variant">
          <span className="material-symbols-outlined text-[32px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-code-sm">Loading FalkorDB Subgraph...</span>
        </div>
      ) : !primary ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container-lowest border border-outline-variant/20 flex flex-col items-center justify-center text-center gap-3">
          <div className="w-12 h-12 rounded-full bg-surface-container flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[28px]">hub</span>
          </div>
          <span className="font-headline-sm text-headline-sm text-primary font-medium">
            Graph Data Unavailable
          </span>
          <p className="font-body-sm text-body-sm text-on-surface-variant max-w-md">
            Start the Spring Boot backend (`mvnw.cmd spring-boot:run`) to load the actual graph topology from FalkorDB.
          </p>
          <button
            onClick={onRefresh}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md transition-colors border border-outline-variant/30 flex items-center gap-1.5"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Refresh Subgraph
          </button>
        </div>
      ) : (
        <div className="flex flex-col rounded-2xl overflow-hidden border border-outline-variant/20 bg-surface-container-lowest shadow-md">
          {/* SVG Canvas */}
          <div className="relative w-full h-[400px] overflow-hidden select-none bg-surface-container-lowest">
            <div
              className="absolute inset-0 opacity-20 pointer-events-none"
              style={{
                backgroundImage: 'radial-gradient(#8f9282 1px, transparent 1px)',
                backgroundSize: '20px 20px',
              }}
            />

            <svg className="absolute inset-0 w-full h-full pointer-events-none" viewBox="0 0 390 400" fill="none">
              <defs>
                <marker id="map-arrow-active" markerWidth="7" markerHeight="7" refX="6" refY="3.5" orient="auto">
                  <path d="M0,0 L0,7 L7,3.5 z" fill="#b3d17a" />
                </marker>
                <marker id="map-arrow-dim" markerWidth="6" markerHeight="6" refX="5" refY="3" orient="auto">
                  <path d="M0,0 L0,6 L6,3 z" fill="#7bd0ff" />
                </marker>
              </defs>

              {/* Real Relationships */}
              {/* Document -> Decision */}
              <path d="M 60 70 L 140 100" stroke="#7bd0ff" strokeWidth="2" strokeDasharray="3 3" markerEnd="url(#map-arrow-dim)" />
              <rect x="75" y="70" width="70" height="13" rx="2" fill="#111318" />
              <text x="80" y="80" fill="#7bd0ff" fontSize="7.5" fontFamily="Geist" fontWeight="600">:SUPPORTED_BY</text>

              {/* Decision -> Project */}
              <path d="M 160 125 L 200 185" stroke="#b3d17a" strokeWidth="2.5" markerEnd="url(#map-arrow-active)" />
              <rect x="155" y="145" width="46" height="13" rx="2" fill="#111318" />
              <text x="160" y="155" fill="#b3d17a" fontSize="8" fontFamily="Geist" fontWeight="600">:AFFECTS</text>

              {/* Team -> Project */}
              <path d="M 80 230 L 180 215" stroke="#b3d17a" strokeWidth="2" strokeDasharray="3 3" markerEnd="url(#map-arrow-active)" />
              <rect x="110" y="215" width="38" height="13" rx="2" fill="#111318" />
              <text x="115" y="225" fill="#b3d17a" fontSize="8" fontFamily="Geist" fontWeight="600">:OWNS</text>

              {/* Project -> System */}
              <path d="M 240 220 L 310 270" stroke="#b3d17a" strokeWidth="2.5" markerEnd="url(#map-arrow-active)" />
              <rect x="250" y="235" width="36" height="13" rx="2" fill="#111318" />
              <text x="254" y="245" fill="#b3d17a" fontSize="8" fontFamily="Geist" fontWeight="600">:USES</text>
            </svg>

            {/* Interactive Node Overlays */}
            {/* Document Node */}
            <div
              onClick={() => setSelectedNodeId('document')}
              className={`absolute left-8 top-12 flex flex-col items-center cursor-pointer group active:scale-95 transition-all ${
                selectedNodeId === 'document' ? 'scale-110 z-20' : 'z-10'
              }`}
            >
              <div
                className={`w-11 h-11 rounded-lg flex items-center justify-center shadow-md border ${
                  selectedNodeId === 'document'
                    ? 'bg-tertiary-container/30 border-tertiary-container text-tertiary-container'
                    : 'bg-surface-container-high border-outline-variant/30 text-on-surface-variant'
                }`}
              >
                <span className="material-symbols-outlined text-[20px]">description</span>
              </div>
              <span className="font-code-sm text-[9px] text-tertiary-container mt-1 bg-surface-dim/90 px-1 rounded truncate max-w-[80px]">
                {primary.documentTitle}
              </span>
              <span className="font-label-sm text-[8px] text-on-surface-variant">{primary.sourceId}</span>
            </div>

            {/* Decision Node */}
            <div
              onClick={() => setSelectedNodeId('decision')}
              className={`absolute left-32 top-24 flex flex-col items-center cursor-pointer group active:scale-95 transition-all ${
                selectedNodeId === 'decision' ? 'scale-110 z-20' : 'z-10'
              }`}
            >
              <div
                className={`w-11 h-11 rounded-lg flex items-center justify-center shadow-md border ${
                  selectedNodeId === 'decision'
                    ? 'bg-primary-container/30 border-primary-container text-primary-container'
                    : 'bg-surface-container-high border-outline-variant/30 text-secondary'
                }`}
              >
                <span className="material-symbols-outlined text-[20px]">gavel</span>
              </div>
              <span className="font-headline-sm text-[10px] text-on-surface mt-1 bg-surface-dim/90 px-1 rounded truncate max-w-[100px]">
                {primary.decision}
              </span>
              <span className="font-code-sm text-[8px] text-on-surface-variant">Decision</span>
            </div>

            {/* Team Node */}
            <div
              onClick={() => setSelectedNodeId('team')}
              className={`absolute left-10 top-52 flex flex-col items-center cursor-pointer group active:scale-95 transition-all ${
                selectedNodeId === 'team' ? 'scale-110 z-20' : 'z-10'
              }`}
            >
              <div
                className={`w-10 h-10 rounded-full flex items-center justify-center shadow-md border ${
                  selectedNodeId === 'team'
                    ? 'bg-secondary/30 border-secondary text-secondary'
                    : 'bg-surface-container-high border-outline-variant/30 text-secondary'
                }`}
              >
                <span className="material-symbols-outlined text-[18px]">groups</span>
              </div>
              <span className="font-code-sm text-[10px] text-secondary mt-1 bg-surface-dim/90 px-1 rounded">
                {primary.ownerTeam}
              </span>
              <span className="font-label-sm text-[8px] text-on-surface-variant">Team Squad</span>
            </div>

            {/* Project Node (Focus/Core) */}
            <div
              onClick={() => setSelectedNodeId('project')}
              className={`absolute left-44 top-48 flex flex-col items-center cursor-pointer active:scale-95 transition-all ${
                selectedNodeId === 'project' ? 'scale-110 z-30' : 'z-10'
              }`}
            >
              <div
                className={`w-14 h-14 p-1 rounded-xl flex items-center justify-center shadow-lg border ${
                  selectedNodeId === 'project'
                    ? 'bg-primary-container/20 border-primary-container'
                    : 'bg-surface-container border-outline-variant/40'
                }`}
              >
                <div className="w-11 h-11 rounded-lg bg-primary-container flex items-center justify-center">
                  <span className="material-symbols-outlined text-on-primary-fixed text-[24px]">dataset</span>
                </div>
              </div>
              <span className="font-headline-sm text-[11px] text-primary-container font-semibold mt-1 bg-surface-dim/95 px-1.5 py-0.5 rounded shadow">
                {primary.project}
              </span>
              <span className="font-code-sm text-[9px] text-on-surface-variant">Project Service</span>
            </div>

            {/* System Node */}
            <div
              onClick={() => setSelectedNodeId('system')}
              className={`absolute right-12 bottom-10 flex flex-col items-center cursor-pointer group active:scale-95 transition-all ${
                selectedNodeId === 'system' ? 'scale-110 z-20' : 'z-10'
              }`}
            >
              <div
                className={`w-12 h-12 rounded-full flex items-center justify-center shadow-md border ${
                  selectedNodeId === 'system'
                    ? 'bg-secondary-container/40 border-secondary text-secondary'
                    : 'bg-surface-container-high border-outline-variant/30 text-secondary'
                }`}
              >
                <span className="material-symbols-outlined text-[22px]">database</span>
              </div>
              <span className="font-code-sm text-[10px] text-secondary mt-1 font-medium bg-surface-dim/90 px-1 rounded">
                {primary.system}
              </span>
              <span className="font-label-sm text-[8px] text-on-surface-variant">Data Store</span>
            </div>

            {/* Active Path Breadcrumb Pill */}
            <div className="absolute bottom-3 left-3 flex items-center gap-1.5 bg-surface-dim/90 backdrop-blur-md px-2.5 py-1.5 rounded-full shadow-md z-20 border border-outline-variant/20">
              <span className="w-2 h-2 rounded-full bg-primary-container animate-pulse" />
              <span className="font-label-sm text-[10px] text-on-surface-variant uppercase tracking-wider">Path</span>
              <span className="font-code-sm text-[10px] text-primary-container font-semibold">
                {primary.decision} → {primary.project} → {primary.system}
              </span>
            </div>
          </div>

          {/* Bottom Entity Inspector Sheet */}
          {activeNode && (
            <div className="w-full bg-surface-container border-t border-outline-variant/20 flex flex-col p-space-md gap-space-md">
              <div className="flex items-start justify-between gap-space-sm">
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-space-xs">
                    <span className="font-headline-sm text-headline-sm font-semibold text-primary truncate">
                      {activeNode.name}
                    </span>
                    <span className="font-code-sm text-[10px] uppercase tracking-wide px-1.5 py-0.5 rounded bg-primary-container/20 text-primary-container font-bold border border-primary-container/30">
                      {activeNode.type}
                    </span>
                  </div>
                  <div className="flex items-center gap-space-xs mt-0.5">
                    <span className="font-label-md text-label-md text-secondary font-medium">
                      {activeNode.subText}
                    </span>
                    <span className="text-outline-variant text-[10px]">•</span>
                    <span className="font-label-sm text-label-sm text-tertiary-container">
                      {activeNode.badge}
                    </span>
                  </div>
                </div>

                <button
                  onClick={() => onNavigateToAsk(`What is the relationship between ${activeNode.name} and other entities?`)}
                  className="px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary text-label-sm flex items-center gap-1 transition-colors border border-outline-variant/20"
                >
                  <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                  <span>Investigate</span>
                </button>
              </div>

              {/* Known Technical Properties */}
              <div className="p-space-sm rounded-xl bg-surface-container-low border border-outline-variant/20 flex flex-col gap-space-xs">
                <span className="font-label-sm text-[10px] text-outline uppercase tracking-wider font-semibold">
                  Known Graph Properties
                </span>
                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2 mt-0.5">
                  {Object.entries(activeNode.properties).map(([key, val]) => (
                    <div key={key} className="flex flex-col">
                      <span className="font-body-sm text-[11px] text-on-surface-variant">{key}</span>
                      <span className="font-code-sm text-code-sm text-on-surface font-medium truncate">
                        {val}
                      </span>
                    </div>
                  ))}
                </div>
              </div>

              {/* Graph Topology: Incoming & Outgoing */}
              <div className="flex flex-col gap-space-sm">
                <div className="flex items-center justify-between">
                  <span className="font-headline-sm text-headline-sm text-primary font-semibold">
                    Connected Relationships
                  </span>
                  <span className="font-code-sm text-[11px] text-on-surface-variant">
                    {activeNode.incoming.length + activeNode.outgoing.length} Edges
                  </span>
                </div>

                <div className="grid grid-cols-1 sm:grid-cols-2 gap-2">
                  {/* Incoming */}
                  <div className="flex flex-col gap-1.5">
                    <span className="text-on-surface-variant font-label-sm text-[10px] uppercase tracking-wider flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">arrow_downward</span>
                      Incoming ({activeNode.incoming.length})
                    </span>
                    {activeNode.incoming.length === 0 ? (
                      <span className="text-[11px] text-outline italic">Root entity in current chain</span>
                    ) : (
                      activeNode.incoming.map((inc, i) => (
                        <div
                          key={i}
                          className="flex items-center justify-between p-2 rounded-lg bg-surface-container-low border border-outline-variant/20"
                        >
                          <span className="font-body-md text-body-md text-on-surface font-medium truncate">
                            {inc.source}
                          </span>
                          <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-primary-container font-semibold">
                            {inc.edge}
                          </span>
                        </div>
                      ))
                    )}
                  </div>

                  {/* Outgoing */}
                  <div className="flex flex-col gap-1.5">
                    <span className="text-on-surface-variant font-label-sm text-[10px] uppercase tracking-wider flex items-center gap-1">
                      <span className="material-symbols-outlined text-[14px]">arrow_upward</span>
                      Outgoing ({activeNode.outgoing.length})
                    </span>
                    {activeNode.outgoing.length === 0 ? (
                      <span className="text-[11px] text-outline italic">Terminal node in current chain</span>
                    ) : (
                      activeNode.outgoing.map((out, i) => (
                        <div
                          key={i}
                          className="flex items-center justify-between p-2 rounded-lg bg-surface-container-low border border-outline-variant/20"
                        >
                          <span className="font-body-md text-body-md text-on-surface font-medium truncate">
                            {out.target}
                          </span>
                          <span className="font-code-sm text-[10px] px-1.5 py-0.5 rounded bg-surface-container-high text-secondary font-semibold">
                            {out.edge}
                          </span>
                        </div>
                      ))
                    )}
                  </div>
                </div>
              </div>

              {/* Verified Evidence Attachment */}
              {activeNode.evidenceDoc && (
                <div className="p-space-sm rounded-xl bg-surface-container-low border border-outline-variant/20 flex items-center justify-between gap-space-sm">
                  <div className="flex items-center gap-space-sm min-w-0">
                    <div className="w-8 h-8 rounded-lg bg-tertiary-container/15 flex items-center justify-center flex-shrink-0 text-tertiary-container">
                      <span className="material-symbols-outlined text-[18px]">verified</span>
                    </div>
                    <div className="flex flex-col min-w-0">
                      <span className="font-label-sm text-label-sm text-on-surface font-medium truncate">
                        {activeNode.evidenceDoc}
                      </span>
                      <span className="font-body-sm text-[11px] text-tertiary-container">
                        Source ID: {activeNode.sourceId || 'RFC'} • Ground-truth graph record
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
