import React, { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  getKnowledgeGraph,
  type GraphDataResponse,
  type GraphNode,
  type GraphRelationship,
} from '../services/api';

interface KnowledgeMapProps {
  onNavigateToAsk: (query: string) => void;
}

interface ConnectedRelationship {
  nodeKey: string;
  name: string;
  edge: string;
  edgeId: string;
  type: string;
  direction: 'incoming' | 'outgoing';
}

interface GraphNodeData {
  id: string;
  name: string;
  type: string;
  subText: string;
  x: number;
  y: number;
  radius: number;
  colorVar: string;
  properties: Record<string, string>;
  incoming: ConnectedRelationship[];
  outgoing: ConnectedRelationship[];
  sourceId?: string;
}

interface GraphEdgeData {
  id: string;
  sourceKey: string;
  targetKey: string;
  label: string;
  x1: number;
  y1: number;
  x2: number;
  y2: number;
  labelX: number;
  labelY: number;
  colorVar: string;
}

const VIEW_W = 860;
const VIEW_H = 560;

function colorForType(type: string): string {
  const t = type.toLowerCase();
  if (t.includes('project') || t.includes('team') || t.includes('repo')) {
    return 'var(--graph-edge-lime)';
  }
  if (t.includes('document') || t.includes('commit') || t.includes('chunk') || t.includes('page')) {
    return 'var(--graph-edge-emerald)';
  }
  return 'var(--graph-edge-cyan)';
}

function shortLabel(value: string, max = 18): string {
  return value.length > max ? value.slice(0, max - 2) + '...' : value;
}

function layoutPositions(ids: string[], centerId?: string | null): Record<string, { x: number; y: number }> {
  const positions: Record<string, { x: number; y: number }> = {};
  const count = ids.length;
  if (count === 0) return positions;
  if (count === 1) {
    positions[ids[0]] = { x: VIEW_W / 2, y: VIEW_H / 2 };
    return positions;
  }
  const cx = VIEW_W / 2;
  const cy = VIEW_H / 2;

  // When a central/selected node is present, place it at center and orbit its neighbors
  if (centerId && ids.includes(centerId)) {
    positions[centerId] = { x: cx, y: cy };
    const neighborIds = ids.filter(id => id !== centerId);
    const n = neighborIds.length;
    if (n === 0) return positions;

    // Distribute neighbors in 1 or 2 rings around center
    const outerCount = n <= 8 ? n : Math.ceil(n * 0.6);
    const innerCount = n - outerCount;

    if (innerCount > 0) {
      const rxInner = 195;
      const ryInner = 135;
      const innerList = neighborIds.slice(0, innerCount);
      for (let i = 0; i < innerList.length; i++) {
        const angle = (2 * Math.PI * i) / innerList.length - Math.PI / 2;
        positions[innerList[i]] = {
          x: Math.round(cx + rxInner * Math.cos(angle)),
          y: Math.round(cy + ryInner * Math.sin(angle)),
        };
      }

      const rxOuter = 325;
      const ryOuter = 215;
      const outerList = neighborIds.slice(innerCount);
      const phaseOffset = Math.PI / outerList.length;
      for (let i = 0; i < outerList.length; i++) {
        const angle = (2 * Math.PI * i) / outerList.length - Math.PI / 2 + phaseOffset;
        positions[outerList[i]] = {
          x: Math.round(cx + rxOuter * Math.cos(angle)),
          y: Math.round(cy + ryOuter * Math.sin(angle)),
        };
      }
    } else {
      const rx = 245;
      const ry = 170;
      for (let i = 0; i < n; i++) {
        const angle = (2 * Math.PI * i) / n - Math.PI / 2;
        positions[neighborIds[i]] = {
          x: Math.round(cx + rx * Math.cos(angle)),
          y: Math.round(cy + ry * Math.sin(angle)),
        };
      }
    }
    return positions;
  }

  if (count <= 4) {
    const r = 160;
    for (let i = 0; i < count; i++) {
      const angle = (2 * Math.PI * i) / count - Math.PI / 2;
      positions[ids[i]] = {
        x: Math.round(cx + r * Math.cos(angle)),
        y: Math.round(cy + r * Math.sin(angle)),
      };
    }
    return positions;
  }

  // 1 or 2 rings distributed on ellipses matched to aspect ratio
  const outerCount = count <= 10 ? count : Math.ceil(count * 0.65);
  const innerCount = count - outerCount;

  // Outer ring
  const rxOuter = cx - 120;
  const ryOuter = cy - 90;
  for (let i = 0; i < outerCount; i++) {
    const angle = (2 * Math.PI * i) / outerCount - Math.PI / 2;
    positions[ids[i]] = {
      x: Math.round(cx + rxOuter * Math.cos(angle)),
      y: Math.round(cy + ryOuter * Math.sin(angle)),
    };
  }

  // Inner ring if needed
  if (innerCount > 0) {
    const rxInner = rxOuter * 0.52;
    const ryInner = ryOuter * 0.52;
    const phaseOffset = Math.PI / innerCount;
    for (let i = 0; i < innerCount; i++) {
      const angle = (2 * Math.PI * i) / innerCount - Math.PI / 2 + phaseOffset;
      positions[ids[outerCount + i]] = {
        x: Math.round(cx + rxInner * Math.cos(angle)),
        y: Math.round(cy + ryInner * Math.sin(angle)),
      };
    }
  }

  return positions;
}

function buildGraphModel(data: GraphDataResponse): {
  nodes: Record<string, GraphNodeData>;
  edges: GraphEdgeData[];
} {
  const nodeList: GraphNode[] = data.nodes || [];
  const relList: GraphRelationship[] = data.relationships || [];
  const positions = layoutPositions(nodeList.map(n => n.id));
  const nodes: Record<string, GraphNodeData> = {};

  for (const node of nodeList) {
    const pos = positions[node.id] || { x: VIEW_W / 2, y: VIEW_H / 2 };
    const properties = { ...(node.properties || {}) };
    nodes[node.id] = {
      id: node.id,
      name: node.name || node.id,
      type: node.type || 'Node',
      subText: node.type || 'Node',
      x: pos.x,
      y: pos.y,
      radius: 24,
      colorVar: colorForType(node.type || 'Node'),
      properties,
      incoming: [],
      outgoing: [],
      sourceId: node.sourceId,
    };
  }

  const edges: GraphEdgeData[] = [];
  for (const rel of relList) {
    const source = nodes[rel.source];
    const target = nodes[rel.target];
    if (!source || !target) continue;
    const label = rel.type ? `:${rel.type}` : ':RELATED';
    edges.push({
      id: rel.id,
      sourceKey: rel.source,
      targetKey: rel.target,
      label,
      x1: source.x,
      y1: source.y,
      x2: target.x,
      y2: target.y,
      labelX: (source.x + target.x) / 2,
      labelY: (source.y + target.y) / 2,
      colorVar: source.colorVar,
    });
    source.outgoing.push({
      nodeKey: target.id,
      name: target.name,
      edge: label,
      edgeId: rel.id,
      type: target.type,
      direction: 'outgoing',
    });
    target.incoming.push({
      nodeKey: source.id,
      name: source.name,
      edge: label,
      edgeId: rel.id,
      type: source.type,
      direction: 'incoming',
    });
  }

  return { nodes, edges };
}

export const KnowledgeMap: React.FC<KnowledgeMapProps> = ({ onNavigateToAsk }) => {
  const [graph, setGraph] = useState<GraphDataResponse | null>(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState<string | null>(null);
  const [filterQuery, setFilterQuery] = useState('');
  const [category, setCategory] = useState<string>('all');
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null);
  const [selectedEdgeId, setSelectedEdgeId] = useState<string | null>(null);
  const [zoomLevel, setZoomLevel] = useState(1);
  const [highlightPath, setHighlightPath] = useState(true);
  const [focusNeighborhood, setFocusNeighborhood] = useState(true);

  const loadGraph = useCallback(async () => {
    setLoading(true);
    setError(null);
    try {
      const data = await getKnowledgeGraph();
      setGraph(data);
    } catch (err: unknown) {
      setGraph(null);
      setError(err instanceof Error ? err.message : 'Failed to load knowledge graph.');
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    loadGraph();
  }, [loadGraph]);

  const { nodes, edges } = useMemo(() => {
    if (!graph) return { nodes: {} as Record<string, GraphNodeData>, edges: [] as GraphEdgeData[] };
    return buildGraphModel(graph);
  }, [graph]);

  const typeCounts = useMemo(() => {
    const counts = new Map<string, number>();
    Object.values(nodes).forEach(node => {
      counts.set(node.type, (counts.get(node.type) || 0) + 1);
    });
    return [...counts.entries()].sort((a, b) => a[0].localeCompare(b[0]));
  }, [nodes]);

  useEffect(() => {
    const ids = Object.keys(nodes);
    if (ids.length === 0) {
      setSelectedNodeId(null);
      return;
    }
    setSelectedNodeId(prev => (prev && nodes[prev] ? prev : ids[0]));
    setSelectedEdgeId(null);
  }, [nodes]);

  const activeEdge = useMemo<GraphEdgeData | null>(() => {
    if (!selectedEdgeId) return null;
    return edges.find(e => e.id === selectedEdgeId) || null;
  }, [selectedEdgeId, edges]);

  const activeNode = useMemo<GraphNodeData | null>(() => {
    if (selectedNodeId && nodes[selectedNodeId]) return nodes[selectedNodeId];
    if (selectedEdgeId) return null;
    return Object.values(nodes)[0] || null;
  }, [selectedNodeId, selectedEdgeId, nodes]);

  const connectedNodeKeys = useMemo<Set<string>>(() => {
    const set = new Set<string>();
    if (selectedEdgeId && activeEdge) {
      set.add(activeEdge.sourceKey);
      set.add(activeEdge.targetKey);
      return set;
    }
    if (selectedNodeId) {
      set.add(selectedNodeId);
      edges.forEach(e => {
        if (e.sourceKey === selectedNodeId) set.add(e.targetKey);
        if (e.targetKey === selectedNodeId) set.add(e.sourceKey);
      });
    }
    return set;
  }, [selectedNodeId, selectedEdgeId, activeEdge, edges]);

  const handleSelectNode = (keyOrName: string) => {
    setSelectedEdgeId(null);
    if (nodes[keyOrName]) {
      setSelectedNodeId(keyOrName);
      return;
    }
    const matchedKey = Object.keys(nodes).find(
      key =>
        nodes[key].name.toLowerCase() === keyOrName.toLowerCase() ||
        nodes[key].id.toLowerCase() === keyOrName.toLowerCase()
    );
    if (matchedKey) setSelectedNodeId(matchedKey);
  };

  const handleSelectEdge = (edgeId: string) => {
    setSelectedEdgeId(edgeId);
    setSelectedNodeId(null);
  };

  const filteredNodeKeys = useMemo(() => {
    return Object.keys(nodes).filter(key => {
      const node = nodes[key];
      if (category !== 'all' && node.type !== category) return false;
      if (filterQuery) {
        const q = filterQuery.toLowerCase();
        return (
          node.name.toLowerCase().includes(q) ||
          node.type.toLowerCase().includes(q) ||
          Object.values(node.properties).some(v => v.toLowerCase().includes(q))
        );
      }
      return true;
    });
  }, [nodes, category, filterQuery]);

  const visibleNodeKeys = useMemo(() => {
    if (!focusNeighborhood || filteredNodeKeys.length <= 12) {
      return filteredNodeKeys;
    }
    const centerKey = selectedNodeId || filteredNodeKeys[0];
    if (!centerKey) return filteredNodeKeys;

    const neighborhood = filteredNodeKeys.filter(
      k => k === centerKey || connectedNodeKeys.has(k)
    );
    if (neighborhood.length <= 3 && filteredNodeKeys.length > 3) {
      const extra = filteredNodeKeys.filter(k => !neighborhood.includes(k)).slice(0, 10);
      return [...neighborhood, ...extra];
    }
    return neighborhood.slice(0, 20);
  }, [focusNeighborhood, filteredNodeKeys, selectedNodeId, connectedNodeKeys]);

  const visibleNodeSet = useMemo(() => new Set(visibleNodeKeys), [visibleNodeKeys]);
  const visibleEdges = useMemo(
    () => edges.filter(edge => visibleNodeSet.has(edge.sourceKey) && visibleNodeSet.has(edge.targetKey)),
    [edges, visibleNodeSet]
  );

  const defaultPositions = useMemo(
    () => layoutPositions(visibleNodeKeys, selectedNodeId),
    [visibleNodeKeys, selectedNodeId]
  );

  const svgRef = useRef<SVGSVGElement | null>(null);
  const [nodePositions, setNodePositions] = useState<Record<string, { x: number; y: number }>>({});
  const [draggingNodeId, setDraggingNodeId] = useState<string | null>(null);
  const dragStartRef = useRef<{ clientX: number; clientY: number; initX: number; initY: number; moved: boolean }>({
    clientX: 0,
    clientY: 0,
    initX: 0,
    initY: 0,
    moved: false,
  });

  const getNodePos = useCallback(
    (id: string) => nodePositions[id] || defaultPositions[id] || { x: VIEW_W / 2, y: VIEW_H / 2 },
    [nodePositions, defaultPositions]
  );

  const handleResetLayout = () => {
    setZoomLevel(1);
    setNodePositions({});
  };

  const handleNodeMouseDown = (e: React.MouseEvent, nodeId: string) => {
    if (e.button !== 0) return;
    e.preventDefault();
    e.stopPropagation();
    const pos = getNodePos(nodeId);
    dragStartRef.current = {
      clientX: e.clientX,
      clientY: e.clientY,
      initX: pos.x,
      initY: pos.y,
      moved: false,
    };
    setDraggingNodeId(nodeId);
    handleSelectNode(nodeId);
  };

  const handleMouseMove = (e: React.MouseEvent) => {
    if (!draggingNodeId || !svgRef.current) return;
    const rect = svgRef.current.getBoundingClientRect();
    if (rect.width === 0 || rect.height === 0) return;
    const dx = ((e.clientX - dragStartRef.current.clientX) * (VIEW_W / rect.width)) / zoomLevel;
    const dy = ((e.clientY - dragStartRef.current.clientY) * (VIEW_H / rect.height)) / zoomLevel;

    if (Math.abs(dx) > 3 || Math.abs(dy) > 3) {
      dragStartRef.current.moved = true;
    }

    const newX = Math.max(35, Math.min(VIEW_W - 35, Math.round(dragStartRef.current.initX + dx)));
    const newY = Math.max(35, Math.min(VIEW_H - 35, Math.round(dragStartRef.current.initY + dy)));

    setNodePositions(prev => ({
      ...prev,
      [draggingNodeId]: { x: newX, y: newY },
    }));
  };

  const handleMouseUp = () => {
    setDraggingNodeId(null);
  };

  useEffect(() => {
    const onGlobalMouseUp = () => setDraggingNodeId(null);
    window.addEventListener('mouseup', onGlobalMouseUp);
    return () => window.removeEventListener('mouseup', onGlobalMouseUp);
  }, []);

  const breadcrumbText = useMemo(() => {
    if (selectedEdgeId && activeEdge) {
      const srcName = nodes[activeEdge.sourceKey]?.name || activeEdge.sourceKey;
      const tgtName = nodes[activeEdge.targetKey]?.name || activeEdge.targetKey;
      return `${srcName} ──[${activeEdge.label}]──▶ ${tgtName}`;
    }
    if (activeNode) {
      return `${activeNode.name} (${activeNode.type})`;
    }
    return 'Interactive Graph';
  }, [activeNode, selectedEdgeId, activeEdge, nodes]);

  const jumpKeys = filteredNodeKeys.slice(0, 24);

  return (
    <div className="flex flex-col w-full px-4 md:px-8 max-w-5xl mx-auto space-y-5 pb-24 pt-3 text-on-surface">
      <section className="bg-surface-container rounded-2xl p-4 md:p-5 border border-outline-variant/30 shadow-sm transition-colors space-y-3">
        <div className="flex flex-col sm:flex-row gap-3 items-stretch sm:items-center justify-between">
          <div className="relative flex-1">
            <span className="material-symbols-outlined text-[19px] text-on-surface-variant absolute left-3.5 top-1/2 -translate-y-1/2 pointer-events-none">
              search
            </span>
            <input
              value={filterQuery}
              onChange={e => setFilterQuery(e.target.value)}
              className="w-full h-10 pl-10 pr-9 bg-surface-container-lowest text-on-surface font-body-sm text-sm rounded-xl placeholder:text-outline border border-outline-variant/40 focus:outline-none focus:ring-2 focus:ring-secondary/40 focus:border-secondary transition-all"
              placeholder="Filter graph nodes by name, type, or property..."
              type="text"
            />
            {filterQuery && (
              <button
                onClick={() => setFilterQuery('')}
                className="absolute right-3 top-1/2 -translate-y-1/2 text-outline hover:text-primary cursor-pointer"
                title="Clear filter"
              >
                <span className="material-symbols-outlined text-[17px]">close</span>
              </button>
            )}
          </div>

          <div className="flex items-center gap-1.5 overflow-x-auto pb-1 sm:pb-0 no-scrollbar">
            <button
              onClick={() => setCategory('all')}
              className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
                category === 'all'
                  ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                  : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
              }`}
            >
              All ({Object.keys(nodes).length})
            </button>
            {typeCounts.map(([type, count]) => (
              <button
                key={type}
                onClick={() => setCategory(type)}
                className={`px-3 py-1.5 rounded-lg font-label-sm text-xs whitespace-nowrap transition-all cursor-pointer ${
                  category === type
                    ? 'bg-primary-container text-on-primary-container font-bold shadow-sm'
                    : 'bg-surface-container-high text-on-surface-variant hover:text-on-surface hover:bg-surface-bright'
                }`}
              >
                {type}s ({count})
              </button>
            ))}
          </div>
        </div>

        {jumpKeys.length > 0 && (
          <div className="flex items-center gap-2 overflow-x-auto pt-2 border-t border-outline-variant/20 no-scrollbar text-xs">
            <span className="text-outline text-xs font-medium flex-shrink-0">Jump to node:</span>
            {jumpKeys.map(key => {
              const node = nodes[key];
              const isSelected = selectedNodeId === key;
              return (
                <button
                  key={key}
                  onClick={() => handleSelectNode(key)}
                  className={`px-2.5 py-1 rounded-lg border transition-all flex items-center gap-1.5 flex-shrink-0 cursor-pointer ${
                    isSelected
                      ? 'bg-secondary-container text-on-secondary-container border-secondary font-bold shadow-sm'
                      : 'bg-surface-container-high border-outline-variant/30 text-on-surface-variant hover:text-on-surface hover:border-outline-variant'
                  }`}
                >
                  <span className="w-2 h-2 rounded-full" style={{ backgroundColor: node.colorVar }} />
                  <span>{node.name}</span>
                </button>
              );
            })}
            {filteredNodeKeys.length > jumpKeys.length && (
              <span className="text-outline flex-shrink-0">
                +{filteredNodeKeys.length - jumpKeys.length} more
              </span>
            )}
          </div>
        )}
      </section>

      {loading ? (
        <div className="w-full h-[420px] rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center gap-3 text-on-surface-variant shadow-sm">
          <span className="material-symbols-outlined text-[36px] text-secondary animate-spin">
            progress_activity
          </span>
          <span className="font-code-sm text-xs">Loading FalkorDB topology...</span>
        </div>
      ) : error ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container border border-error/30 flex flex-col items-center justify-center text-center gap-3 shadow-sm">
          <div className="w-12 h-12 rounded-full bg-error-container/30 flex items-center justify-center text-error">
            <span className="material-symbols-outlined text-[28px]">error</span>
          </div>
          <span className="font-headline-sm text-base text-primary font-bold">
            Graph topology unavailable
          </span>
          <p className="font-body-sm text-xs text-on-surface-variant max-w-md">{error}</p>
          <button
            onClick={loadGraph}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/30 flex items-center gap-1.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Retry graph load
          </button>
        </div>
      ) : Object.keys(nodes).length === 0 ? (
        <div className="w-full p-12 rounded-2xl bg-surface-container border border-outline-variant/30 flex flex-col items-center justify-center text-center gap-3 shadow-sm">
          <div className="w-12 h-12 rounded-full bg-surface-container-high flex items-center justify-center text-outline">
            <span className="material-symbols-outlined text-[28px]">hub</span>
          </div>
          <span className="font-headline-sm text-base text-primary font-bold">
            No nodes returned
          </span>
          <p className="font-body-sm text-xs text-on-surface-variant max-w-md">
            The graph endpoint responded, but {graph?.graph || 'company_brain'} currently has no displayable nodes
            (DocumentChunk nodes are excluded).
          </p>
          <button
            onClick={loadGraph}
            className="px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary font-label-md text-xs font-semibold transition-colors border border-outline-variant/30 flex items-center gap-1.5 cursor-pointer"
          >
            <span className="material-symbols-outlined text-[16px]">refresh</span>
            Refresh topology
          </button>
        </div>
      ) : (
        <div className="flex flex-col rounded-2xl overflow-hidden border border-outline-variant/30 bg-surface-container shadow-sm transition-colors">
          <div className="relative w-full h-[480px] md:h-[540px] overflow-hidden select-none bg-surface-container-lowest">
            <div className="absolute top-3 left-4 flex items-center gap-2 bg-surface-container-high/90 backdrop-blur-md px-3 py-1.5 rounded-xl shadow-md z-20 border border-outline-variant/30">
              <span
                className="w-2.5 h-2.5 rounded-full flex-shrink-0"
                style={{
                  backgroundColor: activeEdge
                    ? activeEdge.colorVar
                    : activeNode?.colorVar || 'var(--graph-edge-cyan)',
                }}
              />
              <span className="font-label-sm text-xs text-on-surface-variant font-medium">
                {activeEdge ? 'Selected Edge:' : 'Selected Node:'}
              </span>
              <span className="font-headline-sm text-xs text-primary font-bold">
                {activeEdge ? activeEdge.label : activeNode?.name}
              </span>
              <span className="font-code-sm text-[10px] uppercase px-1.5 py-0.5 rounded bg-primary-container text-on-primary-container font-bold">
                {activeEdge ? 'RELATIONSHIP' : activeNode?.type}
              </span>
            </div>

            <div className="absolute top-3 right-3 flex flex-col gap-1.5 bg-surface-container-high/90 backdrop-blur-md p-1 rounded-xl shadow-lg z-20 border border-outline-variant/30">
              <button
                onClick={() => setZoomLevel(prev => Math.min(prev + 0.15, 1.6))}
                aria-label="Zoom in"
                title="Zoom in"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">add</span>
              </button>
              <button
                onClick={() => setZoomLevel(prev => Math.max(prev - 0.15, 0.7))}
                aria-label="Zoom out"
                title="Zoom out"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[18px]">remove</span>
              </button>
              <button
                onClick={handleResetLayout}
                aria-label="Reset Layout & Zoom"
                title="Reset Layout & Zoom"
                className="w-8 h-8 flex items-center justify-center rounded-lg bg-surface-container hover:bg-surface-bright text-on-surface transition-colors cursor-pointer"
              >
                <span className="material-symbols-outlined text-[16px]">fit_screen</span>
              </button>
              <button
                onClick={() => setHighlightPath(prev => !prev)}
                aria-label="Path Mode"
                title="Toggle Relationship Paths"
                className={`w-8 h-8 flex items-center justify-center rounded-lg transition-colors cursor-pointer ${
                  highlightPath
                    ? 'bg-primary-container text-on-primary-container shadow-sm'
                    : 'bg-surface-container text-on-surface hover:bg-surface-bright'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">route</span>
              </button>
              <button
                onClick={() => setFocusNeighborhood(prev => !prev)}
                aria-label={focusNeighborhood ? 'Show All Nodes' : 'Focus Connected Neighborhood'}
                title={
                  focusNeighborhood
                    ? 'Showing Focused Neighborhood (Click to show All Nodes)'
                    : 'Showing All Nodes (Click to Focus Neighborhood)'
                }
                className={`w-8 h-8 flex items-center justify-center rounded-lg transition-colors cursor-pointer ${
                  focusNeighborhood
                    ? 'bg-secondary-container text-on-secondary-container shadow-sm'
                    : 'bg-surface-container text-on-surface hover:bg-surface-bright'
                }`}
              >
                <span className="material-symbols-outlined text-[16px]">
                  {focusNeighborhood ? 'filter_center_focus' : 'hub'}
                </span>
              </button>
            </div>

            <svg
              ref={svgRef}
              onMouseMove={handleMouseMove}
              onMouseUp={handleMouseUp}
              onMouseLeave={handleMouseUp}
              className={`w-full h-full select-none ${draggingNodeId ? 'cursor-grabbing' : ''}`}
              viewBox={`0 0 ${VIEW_W} ${VIEW_H}`}
              preserveAspectRatio="xMidYMid meet"
            >
              <defs>
                <marker id="kmap-arrow-cyan" markerWidth="8" markerHeight="8" refX="31" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-cyan)" />
                </marker>
                <marker id="kmap-arrow-lime" markerWidth="8" markerHeight="8" refX="31" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-lime)" />
                </marker>
                <marker id="kmap-arrow-emerald" markerWidth="8" markerHeight="8" refX="31" refY="3.5" orient="auto">
                  <path d="M 0 0 L 7 3.5 L 0 7 z" fill="var(--graph-edge-emerald)" />
                </marker>
                <marker id="kmap-arrow-dim" markerWidth="7" markerHeight="7" refX="30" refY="3.5" orient="auto">
                  <path d="M 0 0 L 6 3.5 L 0 7 z" fill="var(--color-outline)" opacity="0.45" />
                </marker>
                <pattern id="kmap-grid" width="20" height="20" patternUnits="userSpaceOnUse">
                  <circle cx="2" cy="2" r="0.75" fill="var(--color-outline)" opacity="0.25" />
                </pattern>
              </defs>

              <rect width="100%" height="100%" fill="url(#kmap-grid)" />

              <g
                style={{
                  transform: `scale(${zoomLevel})`,
                  transformOrigin: `${VIEW_W / 2}px ${VIEW_H / 2}px`,
                  transition: draggingNodeId ? 'none' : 'transform 0.2s ease-out',
                }}
              >
                <g className="relationships">
                  {visibleEdges.map(edge => {
                    const srcPos = getNodePos(edge.sourceKey);
                    const tgtPos = getNodePos(edge.targetKey);
                    const labelX = (srcPos.x + tgtPos.x) / 2;
                    const labelY = (srcPos.y + tgtPos.y) / 2;
                    const isEdgeSelected = selectedEdgeId === edge.id;
                    const isIncident = selectedNodeId
                      ? edge.sourceKey === selectedNodeId || edge.targetKey === selectedNodeId
                      : isEdgeSelected;
                    const strokeColor =
                      isIncident || isEdgeSelected || highlightPath ? edge.colorVar : 'var(--color-outline)';
                    const opacity = isEdgeSelected ? 1 : isIncident ? 0.95 : highlightPath ? 0.7 : 0.35;
                    const strokeWidth = isEdgeSelected ? 3.5 : isIncident ? 2.5 : 1.8;
                    const markerId =
                      isIncident || isEdgeSelected || highlightPath
                        ? edge.colorVar.includes('lime')
                          ? 'url(#kmap-arrow-lime)'
                          : edge.colorVar.includes('emerald')
                          ? 'url(#kmap-arrow-emerald)'
                          : 'url(#kmap-arrow-cyan)'
                        : 'url(#kmap-arrow-dim)';

                    return (
                      <g
                        key={edge.id}
                        className="cursor-pointer"
                        style={{ opacity, transition: draggingNodeId ? 'none' : 'opacity 0.2s ease' }}
                        onClick={() => handleSelectEdge(edge.id)}
                      >
                        <title>
                          {edge.label} ({nodes[edge.sourceKey]?.name} → {nodes[edge.targetKey]?.name})
                        </title>
                        <line
                          x1={srcPos.x}
                          y1={srcPos.y}
                          x2={tgtPos.x}
                          y2={tgtPos.y}
                          stroke={strokeColor}
                          strokeWidth={strokeWidth}
                          markerEnd={markerId}
                        />
                        <g transform={`translate(${labelX}, ${labelY})`}>
                          <rect
                            x="-44"
                            y="-10"
                            width="88"
                            height="20"
                            rx="5"
                            fill="var(--graph-node-bg)"
                            stroke={isEdgeSelected || isIncident ? strokeColor : 'var(--graph-node-border)'}
                            strokeWidth={isEdgeSelected ? '2' : '1'}
                          />
                          <text
                            x="0"
                            y="3.5"
                            fill={isEdgeSelected || isIncident ? strokeColor : 'var(--color-on-surface-variant)'}
                            fontSize="9"
                            fontFamily="Geist, sans-serif"
                            fontWeight={isEdgeSelected ? '800' : '700'}
                            textAnchor="middle"
                          >
                            {shortLabel(edge.label, 15)}
                          </text>
                        </g>
                      </g>
                    );
                  })}
                </g>

                <g className="nodes">
                  {visibleNodeKeys.map(key => {
                    const node = nodes[key];
                    if (!node) return null;
                    const currentPos = getNodePos(node.id);
                    const isSelected = selectedNodeId === key;
                    const isConnected = connectedNodeKeys.has(key);
                    const opacity = isSelected ? 1 : isConnected ? 0.95 : 0.45;
                    const radius = isSelected ? node.radius + 2 : node.radius;

                    // Place labels above for nodes in upper hemisphere to avoid center edge collisions
                    const isUpper = currentPos.y < VIEW_H / 2 - 25;
                    const labelDy = isUpper ? -(radius + 8) : radius + 15;

                    return (
                      <g
                        key={node.id}
                        onMouseDown={e => handleNodeMouseDown(e, node.id)}
                        onClick={() => {
                          if (!dragStartRef.current.moved) {
                            handleSelectNode(node.id);
                          }
                        }}
                        transform={`translate(${currentPos.x}, ${currentPos.y})`}
                        className={`select-none ${
                          draggingNodeId === node.id ? 'cursor-grabbing' : 'cursor-grab'
                        }`}
                        style={{ opacity, transition: draggingNodeId ? 'none' : 'opacity 0.2s ease' }}
                      >
                        <title>
                          {node.name} ({node.type}) - Drag to reposition
                        </title>
                        {isSelected && (
                          <circle r={radius + 5} fill="none" stroke={node.colorVar} strokeWidth="1.5" strokeDasharray="3 3" opacity="0.8" />
                        )}
                        <circle
                          r={radius}
                          fill="var(--graph-node-bg)"
                          stroke={node.colorVar}
                          strokeWidth={isSelected ? '2.5' : '1.8'}
                        />
                        <text
                          dy="3"
                          fill={node.colorVar}
                          fontSize="9"
                          fontFamily="Geist, sans-serif"
                          fontWeight="800"
                          textAnchor="middle"
                          pointerEvents="none"
                        >
                          {shortLabel(node.type.toUpperCase(), 11)}
                        </text>
                        <text
                          dy={labelDy}
                          fill={isSelected ? node.colorVar : 'var(--graph-text)'}
                          fontSize={isSelected ? '10' : '9'}
                          fontFamily="Geist, sans-serif"
                          fontWeight={isSelected ? '800' : '600'}
                          textAnchor="middle"
                          pointerEvents="none"
                        >
                          {shortLabel(node.name, 22)}
                        </text>
                      </g>
                    );
                  })}
                </g>
              </g>
            </svg>

            <div className="absolute bottom-3 left-4 max-w-[calc(100%-2rem)] flex items-center gap-2 bg-surface/90 backdrop-blur-md px-3 py-1.5 rounded-full shadow-md z-20 border border-outline-variant/30 truncate">
              <span className="w-2 h-2 rounded-full bg-secondary flex-shrink-0" />
              <span className="font-label-sm text-[11px] text-on-surface-variant uppercase tracking-wider font-semibold flex-shrink-0">
                {graph?.graph || 'company_brain'}:
              </span>
              <span className="font-code-sm text-xs text-primary font-bold truncate">{breadcrumbText}</span>
              <span className="font-code-sm text-[10px] text-outline flex-shrink-0">
                {visibleNodeKeys.length < Object.keys(nodes).length
                  ? `Showing ${visibleNodeKeys.length}/${Object.keys(nodes).length} nodes (Neighborhood) · ${visibleEdges.length} rels`
                  : `${Object.keys(nodes).length} nodes · ${visibleEdges.length} rels`}
              </span>
            </div>
          </div>

          {activeEdge ? (
            <div className="w-full bg-surface-container border-t border-outline-variant/30 flex flex-col p-5 md:p-6 gap-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <span className="font-headline-sm text-xl md:text-2xl font-bold text-primary truncate">
                      Relationship: {activeEdge.label}
                    </span>
                    <span className="font-code-sm text-xs uppercase px-2.5 py-0.5 rounded-full bg-secondary-container text-on-secondary-container font-bold">
                      GRAPH RELATIONSHIP
                    </span>
                  </div>
                  <span className="font-body-sm text-xs text-on-surface-variant font-medium mt-1">
                    Returned from {graph?.graph || 'company_brain'}
                  </span>
                </div>
                <button
                  onClick={() =>
                    onNavigateToAsk(
                      `What is the relationship between ${nodes[activeEdge.sourceKey]?.name} and ${nodes[activeEdge.targetKey]?.name}?`
                    )
                  }
                  className="self-start sm:self-center px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary text-xs font-semibold flex items-center gap-1.5 transition-colors border border-outline-variant/30 cursor-pointer shadow-sm flex-shrink-0"
                >
                  <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                  <span>Investigate in Ask Chittix</span>
                </button>
              </div>

              <div className="grid grid-cols-1 md:grid-cols-2 gap-4">
                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col justify-between gap-3">
                  <div className="flex flex-col gap-1">
                    <span className="font-label-sm text-[10px] text-outline uppercase font-semibold">Source</span>
                    <span className="font-headline-sm text-base text-primary font-bold">
                      {nodes[activeEdge.sourceKey]?.name}
                    </span>
                    <span className="font-code-sm text-xs text-secondary">{nodes[activeEdge.sourceKey]?.type}</span>
                  </div>
                  <button
                    onClick={() => handleSelectNode(activeEdge.sourceKey)}
                    className="self-start px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-label-sm text-secondary border border-secondary/30 transition-colors cursor-pointer"
                  >
                    Focus source →
                  </button>
                </div>
                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col justify-between gap-3">
                  <div className="flex flex-col gap-1">
                    <span className="font-label-sm text-[10px] text-outline uppercase font-semibold">Target</span>
                    <span className="font-headline-sm text-base text-primary font-bold">
                      {nodes[activeEdge.targetKey]?.name}
                    </span>
                    <span className="font-code-sm text-xs text-primary">{nodes[activeEdge.targetKey]?.type}</span>
                  </div>
                  <button
                    onClick={() => handleSelectNode(activeEdge.targetKey)}
                    className="self-start px-3 py-1 rounded-lg bg-surface-container hover:bg-surface-container-high text-xs font-label-sm text-primary border border-outline-variant/30 transition-colors cursor-pointer"
                  >
                    Focus target →
                  </button>
                </div>
              </div>
            </div>
          ) : activeNode ? (
            <div className="w-full bg-surface-container border-t border-outline-variant/30 flex flex-col p-5 md:p-6 gap-5">
              <div className="flex flex-col sm:flex-row sm:items-center justify-between gap-3">
                <div className="flex flex-col min-w-0">
                  <div className="flex items-center gap-2.5 flex-wrap">
                    <span className="font-headline-sm text-xl md:text-2xl font-bold text-primary truncate">
                      {activeNode.name}
                    </span>
                    <span className="font-code-sm text-xs uppercase px-2.5 py-0.5 rounded-full bg-primary-container text-on-primary-container font-bold">
                      {activeNode.type}
                    </span>
                    {activeNode.sourceId && (
                      <span className="font-code-sm text-xs text-secondary font-bold px-2 py-0.5 rounded bg-surface-container-high border border-outline-variant/30">
                        Source ID: {activeNode.sourceId}
                      </span>
                    )}
                  </div>
                </div>
                <button
                  onClick={() => onNavigateToAsk(`What do we know about ${activeNode.name}?`)}
                  className="self-start sm:self-center px-4 py-2 rounded-lg bg-surface-container-high hover:bg-surface-bright text-primary text-xs font-semibold flex items-center gap-1.5 transition-colors border border-outline-variant/30 cursor-pointer shadow-sm flex-shrink-0"
                >
                  <span className="material-symbols-outlined text-[16px]">troubleshoot</span>
                  <span>Investigate in Ask Chittix</span>
                </button>
              </div>

              <div className="grid grid-cols-1 lg:grid-cols-2 gap-5">
                <div className="flex flex-col gap-3 p-4 rounded-xl bg-surface-container-low border border-outline-variant/25">
                  <span className="font-label-sm text-xs text-primary uppercase tracking-wider font-bold">
                    Connected relationships ({activeNode.incoming.length + activeNode.outgoing.length})
                  </span>
                  <div className="flex flex-col gap-2 mt-1">
                    {activeNode.incoming.map(rel => (
                      <button
                        key={`in-${rel.edgeId}`}
                        onClick={() => handleSelectNode(rel.nodeKey)}
                        className="group flex items-center justify-between p-3 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/25 transition-all cursor-pointer text-left"
                      >
                        <div className="flex flex-col min-w-0">
                          <span className="font-body-md text-xs font-semibold text-primary truncate">{rel.name}</span>
                          <span className="font-code-sm text-[10px] text-outline">{rel.type}</span>
                        </div>
                        <span
                          onClick={e => {
                            e.stopPropagation();
                            handleSelectEdge(rel.edgeId);
                          }}
                          className="font-code-sm text-xs px-2 py-0.5 rounded bg-surface-container-high text-secondary font-bold"
                        >
                          {rel.edge}
                        </span>
                      </button>
                    ))}
                    {activeNode.outgoing.map(rel => (
                      <button
                        key={`out-${rel.edgeId}`}
                        onClick={() => handleSelectNode(rel.nodeKey)}
                        className="group flex items-center justify-between p-3 rounded-lg bg-surface-container hover:bg-surface-container-high border border-outline-variant/25 transition-all cursor-pointer text-left"
                      >
                        <div className="flex flex-col min-w-0">
                          <span className="font-body-md text-xs font-semibold text-primary truncate">{rel.name}</span>
                          <span className="font-code-sm text-[10px] text-outline">{rel.type}</span>
                        </div>
                        <span
                          onClick={e => {
                            e.stopPropagation();
                            handleSelectEdge(rel.edgeId);
                          }}
                          className="font-code-sm text-xs px-2 py-0.5 rounded bg-surface-container-high text-primary font-bold"
                        >
                          {rel.edge}
                        </span>
                      </button>
                    ))}
                    {activeNode.incoming.length === 0 && activeNode.outgoing.length === 0 && (
                      <div className="p-3 rounded-lg bg-surface-container text-xs text-outline italic">
                        No relationships for this node in the current topology response.
                      </div>
                    )}
                  </div>
                </div>

                <div className="p-4 rounded-xl bg-surface-container-low border border-outline-variant/25 flex flex-col gap-2.5">
                  <span className="font-label-sm text-xs text-outline uppercase tracking-wider font-bold">
                    Properties
                  </span>
                  {Object.keys(activeNode.properties).length === 0 ? (
                    <span className="font-body-sm text-xs text-on-surface-variant">
                      No properties were returned for this node.
                    </span>
                  ) : (
                    <div className="grid grid-cols-1 gap-2.5">
                      {Object.entries(activeNode.properties).map(([key, val]) => (
                        <div
                          key={key}
                          className="flex flex-col sm:flex-row sm:justify-between sm:items-baseline gap-0.5 sm:gap-2 border-b border-outline-variant/15 pb-1.5 last:border-0 last:pb-0"
                        >
                          <span className="font-body-sm text-xs text-on-surface-variant font-medium flex-shrink-0">
                            {key}
                          </span>
                          <span className="font-code-sm text-xs text-primary font-semibold text-left sm:text-right break-all">
                            {val}
                          </span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            </div>
          ) : null}
        </div>
      )}
    </div>
  );
};
