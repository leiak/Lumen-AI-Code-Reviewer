import { useCallback, useEffect, useMemo, useRef, useState } from 'react';
import {
  getGraphTopology,
  getGraph,
  type GraphTopology,
  type GraphLayout,
  type GraphNodeType,
} from '../api';


/**
 * Graph view (v1.1) — structured topology rendered as a clean SVG diagram.
 *
 * Why we don't render the raw mermaid here anymore:
 *  - LangGraph4j's `getGraph(MERMAID)` emits commented duplicate edges and
 *    has no styling on __START__ / __END__, which made the old view look messy.
 *  - The backend now exposes a typed `/api/sessions/x/graph` JSON endpoint
 *    (CouncilGraphTopology). This component turns that JSON into a hand-drawn
 *    SVG with proper colors, legends, layout switching, zoom/pan, fullscreen,
 *    and a source drawer for the raw mermaid text.
 */

const NODE_RADIUS = 36;
const NODE_WIDTH = 168;   // for rectangle (process/decision/etc.)
const NODE_HEIGHT = 56;
const H_GAP = 90;
const V_GAP = 100;
const LAYOUTS: GraphLayout[] = ['TB', 'LR', 'BT', 'RL'];

const NODE_TYPE_LABEL: Record<GraphNodeType, string> = {
  start: '入口',
  end: '终止',
  fanout: '并行扇出',
  process: '业务处理',
  decision: '条件路由',
  review: 'LLM 评审',
};

interface PositionedNode {
  id: string;
  x: number;
  y: number;
  type: GraphNodeType;
  color: string;
  label: string;
  role: string;
}

interface PositionedEdge {
  from: PositionedNode;
  to: PositionedNode;
  kind: 'solid' | 'conditional';
  label: string;
  /** midpoint for label rendering */
  mx: number;
  my: number;
}

function useTopology(autoRefreshMs = 0) {
  const [data, setData] = useState<GraphTopology | null>(null);
  const [error, setError] = useState<string>('');
  const [loading, setLoading] = useState(false);

  const load = useCallback(async () => {
    setLoading(true);
    setError('');
    try {
      const t = await getGraphTopology();
      setData(t);
    } catch (e) {
      setError(String(e));
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => { load(); }, [load]);

  useEffect(() => {
    if (autoRefreshMs <= 0) return;
    const id = setInterval(load, autoRefreshMs);
    return () => clearInterval(id);
  }, [load, autoRefreshMs]);

  return { data, error, loading, reload: load };
}

/**
 * Hand-laid auto layout: column = horizontal rank (BFS from __START__),
 * row = order within a rank. This produces a clean layered graph without
 * needing dagre/elk.
 */
function layoutNodes(topo: GraphTopology, direction: GraphLayout): {
  nodes: PositionedNode[];
  edges: PositionedEdge[];
  width: number;
  height: number;
} {
  if (!topo.nodes.length) return { nodes: [], edges: [], width: 400, height: 200 };

  // Build adjacency for forward + reverse BFS
  const outgoing = new Map<string, string[]>();
  topo.nodes.forEach(n => outgoing.set(n.id, []));
  topo.edges.forEach(e => outgoing.get(e.from)?.push(e.to));

  // BFS from __START__
  const rank = new Map<string, number>();
  rank.set('__START__', 0);
  const queue: string[] = ['__START__'];
  while (queue.length) {
    const cur = queue.shift()!;
    const r = rank.get(cur)!;
    for (const next of outgoing.get(cur) ?? []) {
      if (!rank.has(next)) {
        rank.set(next, r + 1);
        queue.push(next);
      }
    }
  }
  // Any orphan node -> put on last rank
  let maxRank = Math.max(0, ...Array.from(rank.values()));
  for (const n of topo.nodes) {
    if (!rank.has(n.id)) rank.set(n.id, ++maxRank);
  }

  // Group nodes by rank
  const byRank = new Map<number, typeof topo.nodes>();
  topo.nodes.forEach(n => {
    const r = rank.get(n.id)!;
    if (!byRank.has(r)) byRank.set(r, []);
    byRank.get(r)!.push(n);
  });

  // Position
  const positions = new Map<string, PositionedNode>();
  let maxRowWidth = 0;
  const sortedRanks = Array.from(byRank.keys()).sort((a, b) => a - b);
  for (const r of sortedRanks) {
    const row = byRank.get(r)!;
    const rowSpacing = direction === 'LR' || direction === 'RL'
      ? NODE_HEIGHT + V_GAP
      : NODE_WIDTH + H_GAP;
    const colSpacing = direction === 'LR' || direction === 'RL'
      ? NODE_WIDTH + H_GAP * 1.5
      : NODE_HEIGHT + V_GAP * 1.5;

    row.forEach((n, idx) => {
      let x: number;
      let y: number;
      const rowWidth = (row.length - 1) * rowSpacing;
      if (direction === 'TB' || direction === 'BT') {
        x = idx * rowSpacing - rowWidth / 2 + 300;
        y = r * colSpacing;
      } else {
        x = r * colSpacing;
        y = idx * rowSpacing - rowWidth / 2 + 200;
      }
      positions.set(n.id, {
        id: n.id,
        label: n.label,
        type: n.type,
        color: n.color,
        role: n.role,
        x,
        y,
      });
    });
    maxRowWidth = Math.max(maxRowWidth, row.length);
  }

  // Layout dimensions (with padding)
  let minX = Infinity, maxX = -Infinity, minY = Infinity, maxY = -Infinity;
  positions.forEach(p => {
    minX = Math.min(minX, p.x);
    maxX = Math.max(maxX, p.x);
    minY = Math.min(minY, p.y);
    maxY = Math.max(maxY, p.y);
  });
  const PAD = 80;
  const width = Math.max(maxX - minX + PAD * 2, 600);
  const height = Math.max(maxY - minY + PAD * 2, 400);

  // Translate to add padding (origin shift)
  const dx = PAD - minX;
  const dy = PAD - minY;
  positions.forEach(p => { p.x += dx; p.y += dy; });

  // If BT or RL, mirror
  if (direction === 'BT' || direction === 'RL') {
    const w2 = width;
    const h2 = height;
    positions.forEach(p => {
      if (direction === 'BT') p.y = h2 - (p.y - 0);
      else p.x = w2 - (p.x - 0);
    });
  }

  const edges: PositionedEdge[] = topo.edges
    .map(e => {
      const f = positions.get(e.from);
      const t = positions.get(e.to);
      if (!f || !t) return null;
      return {
        from: f,
        to: t,
        kind: e.kind,
        label: e.label,
        mx: (f.x + t.x) / 2,
        my: (f.y + t.y) / 2,
      };
    })
    .filter((x): x is PositionedEdge => x !== null);

  return { nodes: Array.from(positions.values()), edges, width, height };
}

export default function GraphView() {
  const { data, error, loading, reload } = useTopology();
  const [layout, setLayout] = useState<GraphLayout>('TB');
  const [scale, setScale] = useState(1);
  const [pan, setPan] = useState({ x: 0, y: 0 });
  const [showSource, setShowSource] = useState(false);
  const [rawMermaid, setRawMermaid] = useState('');
  const [fetchingRaw, setFetchingRaw] = useState(false);
  const [isFullscreen, setIsFullscreen] = useState(false);

  const svgContainerRef = useRef<HTMLDivElement>(null);
  const dragRef = useRef<{ x: number; y: number; sx: number; sy: number } | null>(null);

  const positioned = useMemo(
    () => data ? layoutNodes(data, layout) : { nodes: [], edges: [], width: 600, height: 400 },
    [data, layout],
  );

  /* ---------- source drawer ---------- */
  const toggleSource = useCallback(async () => {
    if (showSource) { setShowSource(false); return; }
    setShowSource(true);
    if (rawMermaid) return;
    setFetchingRaw(true);
    try {
      const txt = await getGraph('mermaid');
      setRawMermaid(txt);
    } catch (e) {
      setRawMermaid(`(failed to fetch raw mermaid: ${e})`);
    } finally {
      setFetchingRaw(false);
    }
  }, [showSource, rawMermaid]);

  /* ---------- pan ---------- */
  const onPointerDown = (e: React.PointerEvent) => {
    if ((e.target as HTMLElement).closest('[data-node-card]')) return;
    dragRef.current = { x: e.clientX, y: e.clientY, sx: pan.x, sy: pan.y };
    (e.target as HTMLElement).setPointerCapture(e.pointerId);
  };
  const onPointerMove = (e: React.PointerEvent) => {
    if (!dragRef.current) return;
    const dx = e.clientX - dragRef.current.x;
    const dy = e.clientY - dragRef.current.y;
    setPan({ x: dragRef.current.sx + dx, y: dragRef.current.sy + dy });
  };
  const onPointerUp = (e: React.PointerEvent) => {
    dragRef.current = null;
    try { (e.target as HTMLElement).releasePointerCapture(e.pointerId); } catch {}
  };

  /* ---------- wheel zoom ---------- */
  const onWheel = (e: React.WheelEvent) => {
    const factor = e.deltaY < 0 ? 1.1 : 0.9;
    setScale(s => Math.max(0.3, Math.min(2.5, s * factor)));
  };

  /* ---------- fullscreen ---------- */
  useEffect(() => {
    const onFs = () => setIsFullscreen(!!document.fullscreenElement);
    document.addEventListener('fullscreenchange', onFs);
    return () => document.removeEventListener('fullscreenchange', onFs);
  }, []);
  const toggleFullscreen = () => {
    if (!document.fullscreenElement) {
      svgContainerRef.current?.requestFullscreen().catch(() => {});
    } else {
      document.exitFullscreen().catch(() => {});
    }
  };

  const resetView = () => { setScale(1); setPan({ x: 0, y: 0 }); };

  /* ---------- render helpers ---------- */
  const nodeShape = (n: PositionedNode) => {
    const isCircle = n.type === 'start' || n.type === 'end' || n.type === 'decision';
    return isCircle;
  };

  const nodeRadius = (n: PositionedNode) => {
    return n.type === 'decision' ? NODE_RADIUS + 4 : NODE_RADIUS;
  };

  const copySource = () => {
    if (!rawMermaid) return;
    navigator.clipboard?.writeText(rawMermaid).catch(() => {});
  };

  return (
    <div className="space-y-4">
      {/* Header */}
      <div className="flex items-start justify-between gap-4 flex-wrap">
        <div className="min-w-0">
          <h2 className="text-xl font-semibold text-slate-800">
            状态图 · {data?.title ?? '加载中…'}
          </h2>
          <p className="text-sm text-slate-500 mt-1">
            后端编译出的真实拓扑（CouncilGraphTopology v{data?.version ?? '—'}）。
            dispatch 并行扇出到所有 reviewer；条件边根据 signal 路由到 fixer 或 END。
          </p>
        </div>
        {data && (
          <div className="flex flex-wrap items-center gap-2 text-xs text-slate-500 shrink-0">
            <Stat label="节点" value={data.stats.nodeCount} />
            <Stat label="边" value={data.stats.edgeCount} />
            <Stat label="评审员" value={data.stats.reviewerCount} />
            <Stat label="maxRounds" value={data.stats.maxRounds} />
            <Stat label="条件边" value={data.stats.conditionalEdges} />
          </div>
        )}
      </div>

      {/* Toolbar */}
      <div className="flex flex-wrap items-center gap-2 bg-white rounded-lg border border-slate-200 p-2 shadow-sm">
        <ToolGroup label="布局">
          {LAYOUTS.map(l => (
            <button
              key={l}
              onClick={() => setLayout(l)}
              className={`px-2.5 py-1 text-xs rounded ${
                layout === l
                  ? 'bg-council-500 text-white'
                  : 'bg-slate-100 hover:bg-slate-200 text-slate-700'
              }`}
              title={layoutHelp(l)}
            >
              {l}
            </button>
          ))}
        </ToolGroup>

        <div className="w-px h-6 bg-slate-200 mx-1" />

        <ToolGroup label="缩放">
          <button onClick={() => setScale(s => Math.max(0.3, s - 0.1))} className="px-2 py-1 text-xs bg-slate-100 hover:bg-slate-200 rounded">−</button>
          <span className="text-xs text-slate-600 w-12 text-center tabular-nums">{Math.round(scale * 100)}%</span>
          <button onClick={() => setScale(s => Math.min(2.5, s + 0.1))} className="px-2 py-1 text-xs bg-slate-100 hover:bg-slate-200 rounded">+</button>
          <button onClick={resetView} className="px-2 py-1 text-xs bg-slate-100 hover:bg-slate-200 rounded">重置</button>
        </ToolGroup>

        <div className="w-px h-6 bg-slate-200 mx-1" />

        <button
          onClick={toggleSource}
          className={`px-3 py-1 text-xs rounded ${showSource ? 'bg-council-500 text-white' : 'bg-slate-100 hover:bg-slate-200 text-slate-700'}`}
        >
          {showSource ? '关闭源码' : '查看 Mermaid 源码'}
        </button>
        <button
          onClick={toggleFullscreen}
          className="px-3 py-1 text-xs rounded bg-slate-100 hover:bg-slate-200 text-slate-700"
        >
          {isFullscreen ? '退出全屏' : '全屏'}
        </button>
        <button
          onClick={reload}
          disabled={loading}
          className="ml-auto px-3 py-1 text-xs rounded bg-council-50 text-council-700 hover:bg-council-500 hover:text-white disabled:opacity-50"
        >
          {loading ? '刷新中…' : '刷新'}
        </button>
      </div>

      {/* Error */}
      {error && (
        <div className="p-3 rounded border border-critical bg-red-50 text-sm text-red-800 flex items-center justify-between gap-3">
          <span>加载失败：{error}</span>
          <button onClick={reload} className="text-xs px-2 py-1 bg-red-100 hover:bg-red-200 rounded">重试</button>
        </div>
      )}

      {/* Main canvas + legend */}
      <div className="grid grid-cols-1 lg:grid-cols-[1fr_220px] gap-4">
        <div
          ref={svgContainerRef}
          onWheel={onWheel}
          onPointerDown={onPointerDown}
          onPointerMove={onPointerMove}
          onPointerUp={onPointerUp}
          onPointerCancel={onPointerUp}
          className="bg-gradient-to-br from-slate-50 to-slate-100 rounded-lg border border-slate-200 shadow-sm overflow-hidden cursor-grab active:cursor-grabbing select-none"
          style={{ height: isFullscreen ? '100vh' : '640px' }}
        >
          {loading && !data && (
            <div className="h-full flex items-center justify-center text-slate-400 text-sm">
              <div className="flex flex-col items-center gap-2">
                <div className="w-8 h-8 border-2 border-council-500 border-t-transparent rounded-full animate-spin" />
                加载拓扑中…
              </div>
            </div>
          )}

          {!loading && data && positioned.nodes.length === 0 && (
            <div className="h-full flex items-center justify-center text-slate-400 text-sm">
              <EmptyState />
            </div>
          )}

          {data && positioned.nodes.length > 0 && (
            <svg
              viewBox={`0 0 ${positioned.width} ${positioned.height}`}
              preserveAspectRatio="xMidYMid meet"
              width="100%"
              height="100%"
              style={{
                transform: `translate(${pan.x}px, ${pan.y}px) scale(${scale})`,
                transformOrigin: 'center center',
                transition: 'transform 80ms linear',
              }}
            >
              <defs>
                <marker id="arrow" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                  <path d="M0,0 L10,5 L0,10 z" fill="#475569" />
                </marker>
                <marker id="arrow-cond" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto">
                  <path d="M0,0 L10,5 L0,10 z" fill="#f59e0b" />
                </marker>
              </defs>

              {/* edges */}
              {positioned.edges.map((e, idx) => {
                const start = nodeAnchor(e.from, e.to, e.kind, nodeShape(e.from), nodeShape(e.to), nodeRadius(e.from), nodeRadius(e.to));
                const end = nodeAnchor(e.to, e.from, e.kind, nodeShape(e.to), nodeShape(e.from), nodeRadius(e.to), nodeRadius(e.from));
                const isCond = e.kind === 'conditional';
                const stroke = isCond ? '#f59e0b' : '#475569';
                const dash = isCond ? '6 4' : undefined;
                const curvature = isCurved(e.from.id, e.to.id) ? 0.2 : 0;
                const path = curvedPath(start, end, curvature);
                return (
                  <g key={idx}>
                    <path
                      d={path}
                      fill="none"
                      stroke={stroke}
                      strokeWidth={1.6}
                      strokeDasharray={dash}
                      markerEnd={isCond ? 'url(#arrow-cond)' : 'url(#arrow)'}
                    />
                    {e.label && (
                      <g>
                        <rect
                          x={e.mx - (e.label.length * 7 + 8) / 2}
                          y={e.my - 10}
                          width={e.label.length * 7 + 8}
                          height={18}
                          rx={4}
                          fill="white"
                          stroke="#e2e8f0"
                        />
                        <text
                          x={e.mx}
                          y={e.my + 3}
                          textAnchor="middle"
                          fontSize={11}
                          fill={isCond ? '#b45309' : '#475569'}
                          fontFamily="ui-sans-serif, system-ui"
                        >
                          {e.label}
                        </text>
                      </g>
                    )}
                  </g>
                );
              })}

              {/* nodes */}
              {positioned.nodes.map(n => {
                const isCircle = nodeShape(n);
                return (
                  <g key={n.id} transform={`translate(${n.x}, ${n.y})`} data-node-card="1">
                    {isCircle ? (
                      <circle
                        r={nodeRadius(n)}
                        fill="white"
                        stroke={n.color}
                        strokeWidth={2.5}
                      />
                    ) : (
                      <rect
                        x={-NODE_WIDTH / 2}
                        y={-NODE_HEIGHT / 2}
                        width={NODE_WIDTH}
                        height={NODE_HEIGHT}
                        rx={10}
                        fill="white"
                        stroke={n.color}
                        strokeWidth={2.5}
                      />
                    )}
                    <text
                      textAnchor="middle"
                      fontSize={13}
                      fontWeight={600}
                      fill={n.color}
                      fontFamily="ui-sans-serif, system-ui"
                      dy={isCircle ? 5 : -2}
                    >
                      {n.label}
                    </text>
                    <text
                      textAnchor="middle"
                      fontSize={10}
                      fill="#64748b"
                      fontFamily="ui-sans-serif, system-ui"
                      dy={isCircle ? 20 : 14}
                    >
                      {NODE_TYPE_LABEL[n.type]}
                    </text>
                  </g>
                );
              })}
            </svg>
          )}
        </div>

        {/* Legend */}
        {data && (
          <aside className="bg-white rounded-lg border border-slate-200 shadow-sm p-4 self-start">
            <h3 className="text-sm font-semibold text-slate-700 mb-3">节点图例</h3>
            <ul className="space-y-2">
              {data.legend.map(l => (
                <li key={l.type} className="flex items-center gap-2 text-xs text-slate-700">
                  <span
                    className="inline-block w-4 h-4 rounded-full"
                    style={{ backgroundColor: l.color }}
                  />
                  <span className="font-medium">{l.label}</span>
                  <span className="text-slate-400">· {l.type}</span>
                </li>
              ))}
            </ul>
            <h3 className="text-sm font-semibold text-slate-700 mt-5 mb-2">边</h3>
            <ul className="space-y-2 text-xs text-slate-700">
              <li className="flex items-center gap-2">
                <svg width="36" height="10"><line x1="0" y1="5" x2="32" y2="5" stroke="#475569" strokeWidth="1.6" /></svg>
                普通边
              </li>
              <li className="flex items-center gap-2">
                <svg width="36" height="10"><line x1="0" y1="5" x2="32" y2="5" stroke="#f59e0b" strokeWidth="1.6" strokeDasharray="4 3" /></svg>
                条件边
              </li>
            </ul>
            {data.generatedAt && (
              <p className="text-[10px] text-slate-400 mt-4">生成于 {formatTime(data.generatedAt)}</p>
            )}
          </aside>
        )}
      </div>

      {/* Source drawer */}
      {showSource && (
        <div className="bg-slate-900 text-slate-100 rounded-lg border border-slate-700 shadow-sm overflow-hidden">
          <div className="flex items-center justify-between px-3 py-2 border-b border-slate-700">
            <span className="text-xs font-semibold">Mermaid 源码（来自 LangGraph4j）</span>
            <div className="flex gap-2">
              <button onClick={copySource} className="text-xs px-2 py-1 bg-slate-700 hover:bg-slate-600 rounded">复制</button>
              <button onClick={() => setShowSource(false)} className="text-xs px-2 py-1 bg-slate-700 hover:bg-slate-600 rounded">关闭</button>
            </div>
          </div>
          <pre className="text-xs leading-relaxed p-4 overflow-auto max-h-80 font-mono whitespace-pre">
{fetchingRaw ? '加载中…' : rawMermaid || '(空)'}
          </pre>
        </div>
      )}
    </div>
  );
}

/* ---------- small components ---------- */

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <span className="inline-flex items-center gap-1 px-2 py-1 rounded bg-slate-100">
      <span className="font-medium text-slate-700">{value}</span>
      <span className="text-slate-500">{label}</span>
    </span>
  );
}

function ToolGroup({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div className="flex items-center gap-1">
      <span className="text-xs text-slate-400 px-1">{label}</span>
      {children}
    </div>
  );
}

function EmptyState() {
  return (
    <div className="flex flex-col items-center gap-2 text-center max-w-sm">
      <div className="text-3xl">∅</div>
      <p>没有节点。后端 council.yaml 为空或未加载。</p>
    </div>
  );
}

/* ---------- helpers ---------- */

function layoutHelp(l: GraphLayout): string {
  switch (l) {
    case 'TB': return '自顶向下 (默认)';
    case 'BT': return '自底向上';
    case 'LR': return '从左到右';
    case 'RL': return '从右到左';
  }
}

function isCurved(from: string, to: string): boolean {
  // heuristic: bidirectional edges (gate ↔ re_reviewer) curve
  return (from === 'gate' && to === 're_reviewer') || (from === 're_reviewer' && to === 'gate');
}

function curvedPath(from: { x: number; y: number }, to: { x: number; y: number }, curve: number): string {
  if (curve === 0) return `M ${from.x} ${from.y} L ${to.x} ${to.y}`;
  const mx = (from.x + to.x) / 2;
  const my = (from.y + to.y) / 2;
  const dx = to.x - from.x;
  const dy = to.y - from.y;
  // perpendicular offset
  const ox = -dy * curve;
  const oy = dx * curve;
  return `M ${from.x} ${from.y} Q ${mx + ox} ${my + oy} ${to.x} ${to.y}`;
}

/** Compute the connection anchor on a node, given target direction. */
function nodeAnchor(
  self: { x: number; y: number },
  other: { x: number; y: number },
  _kind: 'solid' | 'conditional',
  selfIsCircle: boolean,
  _otherIsCircle: boolean,
  selfR: number,
  _otherR: number,
) {
  const dx = other.x - self.x;
  const dy = other.y - self.y;
  const dist = Math.sqrt(dx * dx + dy * dy) || 1;
  const ux = dx / dist;
  const uy = dy / dist;
  if (selfIsCircle) {
    return { x: self.x + ux * selfR, y: self.y + uy * selfR };
  }
  // rect: clip at box edge
  const halfW = NODE_WIDTH / 2;
  const halfH = NODE_HEIGHT / 2;
  const tx = ux === 0 ? Infinity : halfW / Math.abs(ux);
  const ty = uy === 0 ? Infinity : halfH / Math.abs(uy);
  const t = Math.min(tx, ty);
  return { x: self.x + ux * t, y: self.y + uy * t };
}

function formatTime(iso: string): string {
  try {
    const d = new Date(iso);
    return d.toLocaleTimeString();
  } catch {
    return iso;
  }
}
