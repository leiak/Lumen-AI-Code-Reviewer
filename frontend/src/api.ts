/** API client for the review-council backend. */
const BASE = '/api';

export interface ValidateResult {
  valid: boolean;
  name?: string;
  reviewers?: number;
  maxRounds?: number;
  error?: string;
}

export interface Finding {
  id: string;
  severity: 'critical' | 'major' | 'minor';
  reviewer: string;
  file: string;
  line?: number;
  message: string;
  category: string;
}

export interface StartResponse {
  sessionId: string;
  rounds: number;
  findings: Finding[];
  appliedPatches: number;
  cost: number;
}

export interface CostResponse {
  sessionId: string;
  costUsd: number;
  tokens: number;
}

export interface DemoFinding {
  id: string;
  severity: 'critical' | 'major' | 'minor';
  reviewer: string;
  file: string;
  line: number;
  message: string;
  category: string;
}

export interface DemoPatch {
  id: string;
  file: string;
  status: string;
  description: string;
}

export interface DemoSession {
  sessionId: string;
  rounds: number;
  findings: DemoFinding[];
  appliedPatches: DemoPatch[];
  cost: number;
  demo: boolean;
  note: string;
}

/* ---------- Structured graph (v1.1) ---------- */

export type GraphNodeType =
  | 'start'
  | 'end'
  | 'fanout'
  | 'process'
  | 'decision'
  | 'review';

export interface GraphNode {
  id: string;
  label: string;
  type: GraphNodeType;
  role: string;
  color: string;
}

export type EdgeKind = 'solid' | 'conditional';

export interface GraphEdge {
  from: string;
  to: string;
  kind: EdgeKind;
  label: string;
}

export interface GraphLegendItem {
  type: GraphNodeType;
  label: string;
  color: string;
}

export interface GraphStats {
  nodeCount: number;
  edgeCount: number;
  reviewerCount: number;
  maxRounds: number;
  conditionalEdges: number;
}

export type GraphLayout = 'TB' | 'LR' | 'BT' | 'RL';

export interface GraphTopology {
  title: string;
  layout: GraphLayout;
  generatedAt: string;
  version: string;
  nodes: GraphNode[];
  edges: GraphEdge[];
  legend: GraphLegendItem[];
  stats: GraphStats;
}

export async function validateYaml(yaml: string): Promise<ValidateResult> {
  const r = await fetch(`${BASE}/validate`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ yaml }),
  });
  return r.json();
}

export async function startSession(yaml: string, diff: string, gitRef?: string): Promise<StartResponse> {
  const r = await fetch(`${BASE}/sessions`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ yaml, diff, gitRef }),
  });
  return r.json();
}

export async function getCost(sessionId: string): Promise<CostResponse> {
  const r = await fetch(`${BASE}/sessions/${sessionId}/cost`);
  return r.json();
}

export async function getGraph(format: 'mermaid' | 'plantuml' = 'mermaid'): Promise<string> {
  const r = await fetch(`${BASE}/sessions/x/graph?format=${format}`, {
    headers: { Accept: 'text/plain' },
  });
  return r.text();
}

export async function getGraphTopology(): Promise<GraphTopology> {
  const r = await fetch(`${BASE}/sessions/x/graph`, {
    headers: { Accept: 'application/json' },
  });
  if (!r.ok) throw new Error(`graph topology HTTP ${r.status}: ${await r.text()}`);
  return r.json();
}

export async function getSession(id: string): Promise<unknown> {
  const r = await fetch(`${BASE}/sessions/${id}`);
  if (!r.ok) throw new Error('Session not found');
  return r.json();
}

export async function health(): Promise<{ status: string }> {
  const r = await fetch(`${BASE}/health`);
  return r.json();
}

export async function getDemoSession(): Promise<DemoSession> {
  const r = await fetch(`${BASE}/demo/start`);
  return r.json();
}
