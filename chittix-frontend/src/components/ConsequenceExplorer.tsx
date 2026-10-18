import React, { useState, useEffect, useMemo, useCallback } from 'react';
import {
  getKnowledgeGraph,
  type GraphDataResponse,
  type GraphNode,
  type GraphRelationship,
} from '../services/api';

interface ConsequenceExplorerProps {
  onNavigateToAsk: (query: string) => void;
}

interface ImpactPath {
  id: string;
  steps: {
    nodeId: string;
    nodeName: string;
    nodeType: string;
    sourceId?: string;
    relType?: string;
  }[];
  evidenceIds: string[];
}

function getNodeBadgeColor(type: string): { bg: string; text: string; border: string } {
  const t = (type || '').toLowerCase();
  if (t.includes('project') || t.includes('repo')) {
    return { bg: 'bg-emerald-500/15', text: 'text-emerald-400', border: 'border-emerald-500/30' };
  }
  if (t.includes('decision')) {
    return { bg: 'bg-purple-500/15', text: 'text-purple-400', border: 'border-purple-500/30' };
  }
  if (t.includes('system') || t.includes('service')) {
    return { bg: 'bg-cyan-500/15', text: 'text-cyan-400', border: 'border-cyan-500/30' };
  }
  if (t.includes('team') || t.includes('user')) {
    return { bg: 'bg-amber-500/15', text: 'text-amber-400', border: 'border-amber-500/30' };
  }
  if (t.includes('document')) {
    return { bg: 'bg-blue-500/15', text: 'text-blue-400', border: 'border-blue-500/30' };
  }
  return { bg: 'bg-surface-container-highest', text: 'text-outline', border: 'border-outline-variant/30' };
}

function getNodeIcon(type: string): string {
  const t = (type || '').toLowerCase();
  if (t.includes('project')) return 'folder_open';
  if (t.includes('decision')) return 'gavel';
  if (t.includes('system') || t.includes('service')) return 'dns';
  if (t.includes('team')) return 'groups';
  if (t.includes('document')) return 'description';
  if (t.includes('repo')) return 'terminal';
  return 'hub';
}

export const ConsequenceExplorer: React.FC<ConsequenceExplorerProps> = ({ onNavigateToAsk }) => {
  const [graphData, setGraphData] = useState<GraphDataResponse | null>(null);
  const [loading, setLoading] = useState<boolean>(true);
  const [error, setError] = useState<string | null>(null);

  const [searchQuery, setSearchQuery] = useState<string>('');
  const [selectedType, setSelectedType] = useState<string>('ALL');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);

  const loadGraph = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getKnowledgeGraph();
      setGraphData(data);
      // Auto-select first meaningful entity if none selected
      if (!selectedNodeId && data.nodes && data.nodes.length > 0) {
        // Prefer Decision or Project if available
        const preferred = data.nodes.find(n => {
          const t = (n.type || '').toLowerCase();
          return t.includes('decision') || t.includes('project');
        }) || data.nodes[0];
        setSelectedNodeId(preferred.id ? String(preferred.id) : preferred.name);
      }
    } catch (err: any) {
      setError(err.message || 'Failed to fetch knowledge graph topology from FalkorDB.');
      setGraphData(null);
    } finally {
      setLoading(false);
    }
  }, [selectedNodeId]);

  useEffect(() => {
    loadGraph();
  }, [loadGraph]);

  // Index nodes by ID and name for fast lookup
  const { nodeMap, nodesByType, availableTypes } = useMemo(() => {
    const map = new Map<string, GraphNode>();
    const byType = new Map<string, GraphNode[]>();
    const types = new Set<string>();

    if (graphData?.nodes) {
      for (const node of graphData.nodes) {
        const idKey = node.id ? String(node.id) : node.name;
        map.set(idKey, node);
        if (node.name) {
          map.set(node.name, node);
        }
        const t = node.type || 'Entity';
        types.add(t);
        if (!byType.has(t)) {
          byType.set(t, []);
        }
        byType.get(t)!.push(node);
      }
    }

    return {
      nodeMap: map,
      nodesByType: byType,
      availableTypes: Array.from(types).sort(),
    };
  }, [graphData]);

  // Filtered nodes for the search list
  const filteredNodes = useMemo(() => {
    if (!graphData?.nodes) return [];
    const query = searchQuery.trim().toLowerCase();

    return graphData.nodes.filter(node => {
      const matchesType = selectedType === 'ALL' || (node.type || '').toLowerCase() === selectedType.toLowerCase();
      if (!matchesType) return false;
      if (!query) return true;

      const nameMatch = (node.name || '').toLowerCase().includes(query);
      const typeMatch = (node.type || '').toLowerCase().includes(query);
      const sourceIdMatch = (node.sourceId || '').toLowerCase().includes(query);
      return nameMatch || typeMatch || sourceIdMatch;
    });
  }, [graphData, searchQuery, selectedType]);

  // Currently selected node object
  const selectedNode = useMemo(() => {
    if (!selectedNodeId) return null;
    return nodeMap.get(selectedNodeId) || null;
  }, [selectedNodeId, nodeMap]);

  // Direct connected relationships (incoming & outgoing)
  const { outgoingRels, incomingRels, connectedEvidenceIds } = useMemo(() => {
    if (!selectedNode || !graphData?.relationships) {
      return { outgoingRels: [], incomingRels: [], connectedEvidenceIds: [] };
    }

    const curId = selectedNode.id ? String(selectedNode.id) : selectedNode.name;
    const curName = selectedNode.name;

    const outgoing: { rel: GraphRelationship; targetNode: GraphNode }[] = [];
    const incoming: { rel: GraphRelationship; sourceNode: GraphNode }[] = [];
    const evidenceSet = new Set<string>();

    if (selectedNode.sourceId) {
      evidenceSet.add(selectedNode.sourceId);
    }

    for (const rel of graphData.relationships) {
      const srcStr = String(rel.source);
      const tgtStr = String(rel.target);

      // Check outgoing: selected -> target
      if (srcStr === curId || srcStr === curName) {
        const target = nodeMap.get(tgtStr);
        if (target) {
          outgoing.push({ rel, targetNode: target });
          if (target.sourceId) evidenceSet.add(target.sourceId);
        }
      }

      // Check incoming: source -> selected
      if (tgtStr === curId || tgtStr === curName) {
        const source = nodeMap.get(srcStr);
        if (source) {
          incoming.push({ rel, sourceNode: source });
          if (source.sourceId) evidenceSet.add(source.sourceId);
        }
      }
    }

    return {
      outgoingRels: outgoing,
      incomingRels: incoming,
      connectedEvidenceIds: Array.from(evidenceSet).sort(),
    };
  }, [selectedNode, graphData, nodeMap]);

  // Traceable multi-hop impact paths (up to 2 hops based on real stored topology)
  const traceablePaths = useMemo(() => {
    if (!selectedNode || !graphData?.relationships) return [];

    const curId = selectedNode.id ? String(selectedNode.id) : selectedNode.name;
    const curName = selectedNode.name;
    const paths: ImpactPath[] = [];

    // 1-hop and 2-hop outgoing paths
    for (const out1 of outgoingRels) {
      const target1 = out1.targetNode;
      const target1Id = target1.id ? String(target1.id) : target1.name;

      // Check if target1 has further outgoing edges (hop 2)
      let foundHop2 = false;
      for (const rel2 of graphData.relationships) {
        const src2Str = String(rel2.source);
        const tgt2Str = String(rel2.target);

        if ((src2Str === target1Id || src2Str === target1.name) && tgt2Str !== curId && tgt2Str !== curName) {
          const target2 = nodeMap.get(tgt2Str);
          if (target2 && target2.id !== target1.id) {
            foundHop2 = true;
            const ev = [selectedNode.sourceId, target1.sourceId, target2.sourceId].filter(Boolean) as string[];
            paths.push({
              id: `${out1.rel.id}-${rel2.id}`,
              steps: [
                {
                  nodeId: curId,
                  nodeName: selectedNode.name,
                  nodeType: selectedNode.type || 'Entity',
                  sourceId: selectedNode.sourceId,
                  relType: out1.rel.type,
                },
                {
                  nodeId: target1Id,
                  nodeName: target1.name,
                  nodeType: target1.type || 'Entity',
                  sourceId: target1.sourceId,
                  relType: rel2.type,
                },
                {
                  nodeId: target2.id ? String(target2.id) : target2.name,
                  nodeName: target2.name,
                  nodeType: target2.type || 'Entity',
                  sourceId: target2.sourceId,
                },
              ],
              evidenceIds: Array.from(new Set(ev)),
            });
          }
        }
      }

      // If no hop 2 found, record the direct 1-hop path
      if (!foundHop2) {
        const ev = [selectedNode.sourceId, target1.sourceId].filter(Boolean) as string[];
        paths.push({
          id: `${out1.rel.id}-direct`,
          steps: [
            {
              nodeId: curId,
              nodeName: selectedNode.name,
              nodeType: selectedNode.type || 'Entity',
              sourceId: selectedNode.sourceId,
              relType: out1.rel.type,
            },
            {
              nodeId: target1Id,
              nodeName: target1.name,
              nodeType: target1.type || 'Entity',
              sourceId: target1.sourceId,
            },
          ],
          evidenceIds: Array.from(new Set(ev)),
        });
      }
    }

    // Also include 2-hop incoming paths: N0 -> selectedNode -> target1
    for (const in0 of incomingRels) {
      const src0 = in0.sourceNode;
      const src0Id = src0.id ? String(src0.id) : src0.name;

      for (const out1 of outgoingRels) {
        const tgt1 = out1.targetNode;
        const tgt1Id = tgt1.id ? String(tgt1.id) : tgt1.name;
        if (src0Id !== tgt1Id) {
          const ev = [src0.sourceId, selectedNode.sourceId, tgt1.sourceId].filter(Boolean) as string[];
          paths.push({
            id: `in-${in0.rel.id}-out-${out1.rel.id}`,
            steps: [
              {
                nodeId: src0Id,
                nodeName: src0.name,
                nodeType: src0.type || 'Entity',
                sourceId: src0.sourceId,
                relType: in0.rel.type,
              },
              {
                nodeId: curId,
                nodeName: selectedNode.name,
                nodeType: selectedNode.type || 'Entity',
                sourceId: selectedNode.sourceId,
                relType: out1.rel.type,
              },
              {
                nodeId: tgt1Id,
                nodeName: tgt1.name,
                nodeType: tgt1.type || 'Entity',
                sourceId: tgt1.sourceId,
              },
            ],
            evidenceIds: Array.from(new Set(ev)),
          });
        }
      }
    }

    return paths;
  }, [selectedNode, outgoingRels, incomingRels, graphData, nodeMap]);

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-6xl mx-auto space-y-6 pb-24 pt-4 text-on-surface">
      {/* Header Banner */}
      <section className="p-5 md:p-6 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm">
        <div className="flex flex-wrap items-center justify-between gap-2 mb-2">
          <div className="flex items-center gap-2">
            <span className="material-symbols-outlined text-secondary text-[24px]">alt_route</span>
            <span className="font-headline-sm text-xs uppercase tracking-wider font-bold text-secondary">
              Consequence &amp; Dependency Explorer
            </span>
          </div>
          <div className="flex items-center gap-2">
            <span className="inline-flex items-center gap-1.5 px-2.5 py-0.5 rounded-full bg-tertiary-container/30 text-tertiary font-code-sm text-xs font-bold border border-tertiary/30">
              <span className="w-1.5 h-1.5 rounded-full bg-tertiary" />
              Verified FalkorDB Topology
            </span>
            <button
              onClick={loadGraph}
              disabled={loading}
              className="inline-flex items-center gap-1 px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-container-highest border border-outline-variant/30 text-xs font-code-sm text-on-surface-variant cursor-pointer transition-colors"
            >
              <span className={`material-symbols-outlined text-[14px] ${loading ? 'animate-spin' : ''}`}>
                sync
              </span>
              Refresh
            </button>
          </div>
        </div>

        <h2 className="font-headline-sm text-xl md:text-2xl text-primary font-bold tracking-tight">
          See what depends on what.
        </h2>
        <p className="font-body-sm text-sm text-on-surface-variant mt-1.5 max-w-3xl leading-relaxed">
          Select any entity to trace its stored relationships, connected architecture dependencies,
          and verifiable evidence paths directly from FalkorDB.
        </p>

        {/* Guardrail Disclosure */}
        <div className="mt-4 p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 flex items-start gap-2 text-xs font-body-sm text-on-surface-variant">
          <span className="material-symbols-outlined text-[16px] text-tertiary flex-shrink-0 mt-0.5">
            verified
          </span>
          <div>
            <strong className="text-primary font-semibold">Real Graph Topology Only: </strong>
            All connections below represent verified relationships (<code className="font-mono text-tertiary">AFFECTS</code>, <code className="font-mono text-tertiary">USES</code>, <code className="font-mono text-tertiary">SUPPORTED_BY</code>, <code className="font-mono text-tertiary">OWNS</code>) stored in the database. No synthetic blast radii, simulated failure cascades, or fabricated impact percentages are used.
          </div>
        </div>
      </section>

      {/* Loading State */}
      {loading && !graphData && (
        <div className="p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center gap-3">
          <span className="material-symbols-outlined text-[32px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-body-sm text-sm text-on-surface-variant">
            Loading graph topology from FalkorDB...
          </span>
        </div>
      )}

      {/* Error / Unavailable State */}
      {error && (
        <div className="p-6 rounded-2xl bg-error-container/20 border border-error-container/40 flex flex-col sm:flex-row items-start sm:items-center justify-between gap-4">
          <div className="flex items-start gap-3">
            <span className="material-symbols-outlined text-[24px] text-error flex-shrink-0 mt-0.5">
              cloud_off
            </span>
            <div>
              <h3 className="font-bold text-sm text-error">Graph Service Unavailable</h3>
              <p className="font-body-sm text-xs text-on-surface-variant mt-0.5">{error}</p>
            </div>
          </div>
          <button
            onClick={loadGraph}
            className="px-4 py-2 rounded-lg bg-error text-on-error font-label-md text-xs font-semibold cursor-pointer shadow-sm flex items-center gap-1.5 flex-shrink-0"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry Connection
          </button>
        </div>
      )}

      {/* Main Content Area */}
      {graphData && (
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-6">
          {/* Left Column: Entity Selector & Filter (4 cols) */}
          <div className="lg:col-span-4 space-y-4">
            <div className="p-4 rounded-2xl bg-surface-container border border-outline-variant/30 space-y-3 shadow-sm">
              <div className="flex items-center justify-between">
                <span className="font-headline-sm text-xs font-bold text-primary uppercase tracking-wider flex items-center gap-1.5">
                  <span className="material-symbols-outlined text-[16px] text-secondary">search</span>
                  Select Entity
                </span>
                <span className="font-code-sm text-[11px] text-outline">
                  {filteredNodes.length} / {graphData.nodes?.length || 0}
                </span>
              </div>

              {/* Search input */}
              <div className="relative">
                <span className="material-symbols-outlined absolute left-2.5 top-2.5 text-[16px] text-outline">
                  search
                </span>
                <input
                  type="text"
                  value={searchQuery}
                  onChange={e => setSearchQuery(e.target.value)}
                  placeholder="Search by name, type, source ID..."
                  className="w-full pl-8 pr-3 py-1.5 rounded-lg bg-surface-container-high border border-outline-variant/30 text-xs text-primary placeholder:text-outline focus:outline-none focus:border-primary transition-colors"
                />
                {searchQuery && (
                  <button
                    onClick={() => setSearchQuery('')}
                    className="absolute right-2.5 top-2 text-outline hover:text-primary text-xs cursor-pointer"
                  >
                    <span className="material-symbols-outlined text-[14px]">close</span>
                  </button>
                )}
              </div>

              {/* Type Filter Pills */}
              <div className="flex flex-wrap gap-1 pt-1">
                <button
                  onClick={() => setSelectedType('ALL')}
                  className={`px-2 py-0.5 rounded-full text-[10px] font-code-sm font-semibold transition-colors cursor-pointer ${
                    selectedType === 'ALL'
                      ? 'bg-primary text-on-primary'
                      : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'
                  }`}
                >
                  All ({graphData.nodes?.length || 0})
                </button>
                {availableTypes.map(t => {
                  const count = nodesByType.get(t)?.length || 0;
                  const isSelected = selectedType.toLowerCase() === t.toLowerCase();
                  return (
                    <button
                      key={t}
                      onClick={() => setSelectedType(isSelected ? 'ALL' : t)}
                      className={`px-2 py-0.5 rounded-full text-[10px] font-code-sm font-semibold transition-colors cursor-pointer ${
                        isSelected
                          ? 'bg-primary text-on-primary'
                          : 'bg-surface-container-high text-on-surface-variant hover:bg-surface-container-highest'
                      }`}
                    >
                      {t} ({count})
                    </button>
                  );
                })}
              </div>

              {/* Scrollable Node List */}
              <div className="max-h-[480px] overflow-y-auto space-y-1.5 pr-1 divide-y divide-outline-variant/10">
                {filteredNodes.length === 0 ? (
                  <div className="p-4 text-center text-xs text-outline font-body-sm">
                    No graph entities match your filter.
                  </div>
                ) : (
                  filteredNodes.map(node => {
                    const nodeKey = node.id ? String(node.id) : node.name;
                    const isSelected = selectedNodeId === nodeKey || selectedNodeId === node.name;
                    const badge = getNodeBadgeColor(node.type);
                    const icon = getNodeIcon(node.type);

                    return (
                      <div
                        key={nodeKey}
                        onClick={() => setSelectedNodeId(nodeKey)}
                        className={`p-2.5 rounded-xl border transition-all cursor-pointer flex items-center justify-between gap-2 ${
                          isSelected
                            ? 'bg-primary-container/20 border-primary shadow-sm'
                            : 'bg-surface-container-low hover:bg-surface-container-high border-outline-variant/20'
                        }`}
                      >
                        <div className="min-w-0 flex items-center gap-2">
                          <span className={`material-symbols-outlined text-[18px] ${badge.text} flex-shrink-0`}>
                            {icon}
                          </span>
                          <div className="min-w-0">
                            <div className="font-semibold text-xs text-primary truncate" title={node.name}>
                              {node.name}
                            </div>
                            <div className="flex items-center gap-1.5 mt-0.5">
                              <span className={`font-code-sm text-[9px] px-1.5 py-0.2 rounded font-semibold border ${badge.bg} ${badge.text} ${badge.border}`}>
                                {node.type || 'Entity'}
                              </span>
                              {node.sourceId && (
                                <span className="font-code-sm text-[9px] text-outline truncate">
                                  {node.sourceId}
                                </span>
                              )}
                            </div>
                          </div>
                        </div>

                        <span className="material-symbols-outlined text-[16px] text-outline flex-shrink-0">
                          {isSelected ? 'check_circle' : 'chevron_right'}
                        </span>
                      </div>
                    );
                  })
                )}
              </div>
            </div>
          </div>

          {/* Right Column: Entity Details, Relationships & Traceable Paths (8 cols) */}
          <div className="lg:col-span-8 space-y-4">
            {selectedNode ? (
              <>
                {/* Selected Entity Card */}
                <div className="p-5 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm space-y-3">
                  <div className="flex flex-wrap items-start justify-between gap-2">
                    <div className="space-y-1">
                      <div className="flex items-center gap-2">
                        <span className={`font-code-sm text-[10px] px-2 py-0.5 rounded-full font-bold border ${getNodeBadgeColor(selectedNode.type).bg} ${getNodeBadgeColor(selectedNode.type).text} ${getNodeBadgeColor(selectedNode.type).border}`}>
                          {selectedNode.type || 'Entity'}
                        </span>
                        {selectedNode.sourceId && (
                          <span className="inline-flex items-center gap-1 font-code-sm text-[10px] px-2 py-0.5 rounded-full bg-surface-container-high text-secondary border border-outline-variant/30">
                            <span className="material-symbols-outlined text-[12px]">badge</span>
                            {selectedNode.sourceId}
                          </span>
                        )}
                        <span className="font-code-sm text-[10px] text-outline">
                          ID: {selectedNode.id}
                        </span>
                      </div>
                      <h3 className="font-headline-sm text-lg md:text-xl font-bold text-primary">
                        {selectedNode.name}
                      </h3>
                    </div>

                    <button
                      onClick={() => onNavigateToAsk(`What depends on or is affected by ${selectedNode.name}?`)}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 rounded-lg bg-primary-container text-on-primary-container hover:bg-primary-container/80 text-xs font-semibold cursor-pointer shadow-sm transition-all"
                    >
                      <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                      Ask Chittix about this entity
                    </button>
                  </div>

                  {/* Properties table if any */}
                  {selectedNode.properties && Object.keys(selectedNode.properties).length > 0 && (
                    <div className="pt-2 border-t border-outline-variant/20 grid grid-cols-1 sm:grid-cols-2 gap-2 text-xs font-code-sm">
                      {Object.entries(selectedNode.properties).map(([k, v]) => {
                        if (k === 'name' || k === 'source_id' || k === 'embedding') return null;
                        return (
                          <div key={k} className="p-2 rounded-lg bg-surface-container-low border border-outline-variant/20 flex flex-col">
                            <span className="text-[10px] text-outline uppercase font-semibold">{k}</span>
                            <span className="text-primary truncate mt-0.5">{String(v)}</span>
                          </div>
                        );
                      })}
                    </div>
                  )}
                </div>

                {/* Direct Stored Connections */}
                <div className="p-5 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm space-y-4">
                  <div className="flex items-center justify-between">
                    <span className="font-headline-sm text-xs font-bold text-primary uppercase tracking-wider flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[18px] text-secondary">device_hub</span>
                      Direct Stored Graph Connections
                    </span>
                    <span className="font-code-sm text-[11px] text-outline">
                      {outgoingRels.length} outgoing · {incomingRels.length} incoming
                    </span>
                  </div>

                  <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                    {/* Outgoing: selected -> target */}
                    <div className="space-y-2">
                      <div className="font-code-sm text-xs font-bold text-tertiary flex items-center gap-1">
                        <span className="material-symbols-outlined text-[16px]">arrow_forward</span>
                        Outgoing Relationships ({outgoingRels.length})
                      </div>
                      {outgoingRels.length === 0 ? (
                        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 text-xs text-outline font-body-sm">
                          No outgoing edges recorded in graph.
                        </div>
                      ) : (
                        <div className="space-y-1.5">
                          {outgoingRels.map(({ rel, targetNode }) => {
                            const badge = getNodeBadgeColor(targetNode.type);
                            return (
                              <div
                                key={rel.id}
                                onClick={() => setSelectedNodeId(targetNode.id ? String(targetNode.id) : targetNode.name)}
                                className="p-2.5 rounded-xl bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/20 flex items-center justify-between gap-2 cursor-pointer transition-colors"
                              >
                                <div className="min-w-0 flex items-center gap-2">
                                  <span className="font-code-sm text-[10px] font-bold px-1.5 py-0.5 rounded bg-tertiary-container/30 text-tertiary border border-tertiary/30">
                                    {rel.type}
                                  </span>
                                  <span className="material-symbols-outlined text-[14px] text-outline">
                                    arrow_right_alt
                                  </span>
                                  <div className="min-w-0">
                                    <div className="font-semibold text-xs text-primary truncate">
                                      {targetNode.name}
                                    </div>
                                    <span className={`font-code-sm text-[9px] px-1 py-0.2 rounded border ${badge.bg} ${badge.text} ${badge.border}`}>
                                      {targetNode.type || 'Entity'}
                                    </span>
                                  </div>
                                </div>
                                <span className="material-symbols-outlined text-[16px] text-outline">
                                  chevron_right
                                </span>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>

                    {/* Incoming: source -> selected */}
                    <div className="space-y-2">
                      <div className="font-code-sm text-xs font-bold text-secondary flex items-center gap-1">
                        <span className="material-symbols-outlined text-[16px]">arrow_back</span>
                        Incoming Relationships ({incomingRels.length})
                      </div>
                      {incomingRels.length === 0 ? (
                        <div className="p-3 rounded-xl bg-surface-container-low border border-outline-variant/20 text-xs text-outline font-body-sm">
                          No incoming edges recorded in graph.
                        </div>
                      ) : (
                        <div className="space-y-1.5">
                          {incomingRels.map(({ rel, sourceNode }) => {
                            const badge = getNodeBadgeColor(sourceNode.type);
                            return (
                              <div
                                key={rel.id}
                                onClick={() => setSelectedNodeId(sourceNode.id ? String(sourceNode.id) : sourceNode.name)}
                                className="p-2.5 rounded-xl bg-surface-container-low hover:bg-surface-container-high border border-outline-variant/20 flex items-center justify-between gap-2 cursor-pointer transition-colors"
                              >
                                <div className="min-w-0 flex items-center gap-2">
                                  <div className="min-w-0 text-left">
                                    <div className="font-semibold text-xs text-primary truncate">
                                      {sourceNode.name}
                                    </div>
                                    <span className={`font-code-sm text-[9px] px-1 py-0.2 rounded border ${badge.bg} ${badge.text} ${badge.border}`}>
                                      {sourceNode.type || 'Entity'}
                                    </span>
                                  </div>
                                  <span className="material-symbols-outlined text-[14px] text-outline">
                                    arrow_right_alt
                                  </span>
                                  <span className="font-code-sm text-[10px] font-bold px-1.5 py-0.5 rounded bg-secondary-container/30 text-secondary border border-secondary/30">
                                    {rel.type}
                                  </span>
                                </div>
                                <span className="material-symbols-outlined text-[16px] text-outline">
                                  chevron_right
                                </span>
                              </div>
                            );
                          })}
                        </div>
                      )}
                    </div>
                  </div>
                </div>

                {/* Traceable Graph Impact Paths */}
                <div className="p-5 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm space-y-3">
                  <div className="flex items-center justify-between">
                    <span className="font-headline-sm text-xs font-bold text-primary uppercase tracking-wider flex items-center gap-1.5">
                      <span className="material-symbols-outlined text-[18px] text-primary">route</span>
                      Traceable Impact &amp; Dependency Paths
                    </span>
                    <span className="font-code-sm text-[11px] text-outline">
                      {traceablePaths.length} stored {traceablePaths.length === 1 ? 'path' : 'paths'}
                    </span>
                  </div>

                  {traceablePaths.length === 0 ? (
                    <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/20 text-xs text-on-surface-variant font-body-sm">
                      No multi-hop paths originate from or lead into this node in the stored graph.
                    </div>
                  ) : (
                    <div className="space-y-3">
                      {traceablePaths.map((p, idx) => (
                        <div
                          key={p.id || idx}
                          className="p-3.5 rounded-xl bg-surface-container-low border border-outline-variant/20 space-y-2.5"
                        >
                          <div className="flex flex-wrap items-center gap-1.5 font-code-sm text-xs">
                            {p.steps.map((step, sIdx) => {
                              const badge = getNodeBadgeColor(step.nodeType);
                              return (
                                <React.Fragment key={sIdx}>
                                  <div
                                    onClick={() => setSelectedNodeId(step.nodeId)}
                                    className={`inline-flex items-center gap-1.5 px-2.5 py-1 rounded-lg border cursor-pointer hover:opacity-80 transition-opacity ${
                                      step.nodeId === selectedNode.id || step.nodeName === selectedNode.name
                                        ? 'bg-primary-container/30 border-primary text-primary font-bold'
                                        : `${badge.bg} ${badge.text} ${badge.border}`
                                    }`}
                                  >
                                    <span className="font-semibold">{step.nodeName}</span>
                                    <span className="text-[9px] uppercase opacity-75">({step.nodeType})</span>
                                  </div>

                                  {step.relType && (
                                    <div className="inline-flex items-center gap-0.5 text-secondary px-1">
                                      <span className="font-bold text-[10px] text-secondary">
                                        -[{step.relType}]-&gt;
                                      </span>
                                    </div>
                                  )}
                                </React.Fragment>
                              );
                            })}
                          </div>

                          {p.evidenceIds.length > 0 && (
                            <div className="flex items-center gap-2 pt-1 border-t border-outline-variant/15 text-[11px] font-code-sm text-outline">
                              <span className="flex items-center gap-1 text-secondary">
                                <span className="material-symbols-outlined text-[13px]">description</span>
                                Supporting Evidence:
                              </span>
                              <div className="flex flex-wrap gap-1">
                                {p.evidenceIds.map(evId => (
                                  <span
                                    key={evId}
                                    className="px-1.5 py-0.2 rounded bg-surface-container-highest text-primary font-mono text-[10px]"
                                  >
                                    {evId}
                                  </span>
                                ))}
                              </div>
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  )}
                </div>

                {/* Evidence & Supporting Documents Card */}
                {connectedEvidenceIds.length > 0 && (
                  <div className="p-4 rounded-2xl bg-surface-container border border-outline-variant/30 shadow-sm flex flex-wrap items-center justify-between gap-3">
                    <div className="flex items-center gap-2">
                      <span className="material-symbols-outlined text-secondary text-[20px]">
                        verified_user
                      </span>
                      <div>
                        <div className="font-headline-sm text-xs font-bold text-primary">
                          Connected Source IDs &amp; Architecture Documents
                        </div>
                        <div className="font-body-sm text-[11px] text-on-surface-variant">
                          Retrieved and verified from FalkorDB nodes and relationships.
                        </div>
                      </div>
                    </div>

                    <div className="flex flex-wrap gap-1.5">
                      {connectedEvidenceIds.map(id => (
                        <button
                          key={id}
                          onClick={() => onNavigateToAsk(`What does document ${id} describe regarding ${selectedNode.name}?`)}
                          className="px-2.5 py-1 rounded-lg bg-surface-container-high hover:bg-surface-container-highest border border-outline-variant/30 text-xs font-mono text-secondary hover:text-primary transition-colors cursor-pointer"
                        >
                          {id}
                        </button>
                      ))}
                    </div>
                  </div>
                )}
              </>
            ) : (
              <div className="p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center text-center gap-3 shadow-sm">
                <span className="material-symbols-outlined text-[36px] text-outline">
                  touch_app
                </span>
                <h3 className="font-headline-sm text-base font-bold text-primary">
                  No Entity Selected
                </h3>
                <p className="font-body-sm text-xs text-on-surface-variant max-w-md">
                  Choose an entity from the list on the left to inspect its real connected dependencies,
                  stored relationships, and traceable graph paths.
                </p>
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
};
