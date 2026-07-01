export interface ScriptDto {
  id: string
  name: string
  source: string
  sourceHash: string
  createdAt: string
  updatedAt: string
}

export interface RunDto {
  runId: string
  scriptId: string
  scriptName: string
  status: string
  pid: number
  restartCount: number
  startedAt: string | null
  lastError: string | null
}

export interface MetricSnapshot {
  epochMs: number
  heapUsedBytes: number
  heapMaxBytes: number
  processCpuLoad: number
  tickCount: number
  errorCount: number
  uptimeMs: number
  lastTickMs: number
}

export interface LogEntry {
  epochMs: number
  level: string
  message: string
  thrown: string
}

export interface CreateRunRequest {
  scriptId: string
  tickPeriodMs?: number
  params?: Record<string, string>
  stopOnError?: boolean
  maxHeapBytes?: number
  tickTimeoutMs?: number
  errorThreshold?: number
}

export type NodeKind = 'SCRIPT' | 'MODULE'

export interface FlowNodeModel {
  id: string
  kind: NodeKind
  refId: string
  params?: Record<string, string>
  tickPeriodMs?: number
}

export interface FlowEdgeModel {
  fromNode: string
  fromPort: string
  toNode: string
  toPort: string
}

export interface FlowGraphModel {
  nodes: FlowNodeModel[]
  edges: FlowEdgeModel[]
}

export interface FlowDto {
  id: string
  name: string
  graph: FlowGraphModel
  createdAt: string
  updatedAt: string
}

export interface DeployResponse {
  flowId: string
  nodeRuns: Record<string, string>
}

export interface ModuleDto {
  id: string
  name: string
  version: string
  specJson: string
  source: string
  createdAt: string
}

export interface RunSummary {
  run: RunDto
  latest: MetricSnapshot | null
}

export interface RunHistoryDto {
  runId: string
  scriptId: string
  scriptName: string
  status: string
  pid: number
  restartCount: number
  startedAt: string | null
  endedAt: string | null
  lastError: string | null
  flowId: string | null
  nodeId: string | null
}
