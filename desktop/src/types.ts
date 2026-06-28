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
