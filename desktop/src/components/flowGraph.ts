import type { FlowGraphModel, NodeKind, RunDto } from '../types'

// reactflow 노드/엣지에서 buildGraph가 필요로 하는 최소 형태(테스트에서 reactflow 의존 회피).
export interface CanvasNodeData {
  kind: NodeKind
  refId: string
  tickPeriodMs: number
  params: Record<string, string>
}
export interface CanvasNode {
  id: string
  data: CanvasNodeData
}
export interface CanvasEdge {
  source: string
  target: string
  sourceHandle?: string | null
  targetHandle?: string | null
}

// 캔버스 상태 → 백엔드 플로우 그래프(노드별 설정·포트 포함).
export function buildGraph(nodes: CanvasNode[], edges: CanvasEdge[]): FlowGraphModel {
  return {
    nodes: nodes.map((n) => ({
      id: n.id,
      kind: n.data.kind,
      refId: n.data.refId,
      params: n.data.params,
      tickPeriodMs: n.data.tickPeriodMs,
    })),
    edges: edges.map((e) => ({
      fromNode: e.source,
      fromPort: e.sourceHandle ?? 'out',
      toNode: e.target,
      toPort: e.targetHandle ?? 'in',
    })),
  }
}

// 배포 결과(nodeId→runId)와 현재 실행 목록으로 노드별 상태 매핑.
export function mapNodeStatuses(
  nodeRuns: Record<string, string>,
  runs: RunDto[],
): Record<string, string> {
  const byId = new Map(runs.map((r) => [r.runId, r.status]))
  const out: Record<string, string> = {}
  for (const [nodeId, runId] of Object.entries(nodeRuns)) {
    const st = byId.get(runId)
    if (st) {
      out[nodeId] = st
    }
  }
  return out
}

// params 맵 → "key=value" 줄 텍스트(설정 편집용).
export function paramsToText(params: Record<string, string>): string {
  return Object.entries(params)
    .map(([k, v]) => `${k}=${v}`)
    .join('\n')
}

// 노드가 참조하는 스크립트/모듈의 표시 이름(모듈은 name@version). 못 찾으면 refId.
export function refLabel(
  kind: NodeKind,
  refId: string,
  scripts: { id: string; name: string }[],
  modules: { id: string; name: string; version: string }[],
): string {
  if (kind === 'MODULE') {
    const m = modules.find((x) => x.id === refId)
    return m ? `${m.name}@${m.version}` : refId
  }
  const s = scripts.find((x) => x.id === refId)
  return s ? s.name : refId
}
