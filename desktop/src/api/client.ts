import type {
  CreateRunRequest,
  DeployResponse,
  FlowDto,
  FlowGraphModel,
  LogEntry,
  MetricSnapshot,
  ModuleDto,
  RunDto,
  RunHistoryDto,
  RunSummary,
  ScriptDto,
} from '../types'

interface MaestroBridge {
  backendUrl?: string
}

export const BACKEND_BASE: string =
  (window as unknown as { maestro?: MaestroBridge }).maestro?.backendUrl ??
  import.meta.env.VITE_BACKEND_URL ??
  'http://localhost:8080'

async function http<T>(path: string, init?: RequestInit): Promise<T> {
  const res = await fetch(BACKEND_BASE + path, {
    headers: { 'Content-Type': 'application/json' },
    ...init,
  })
  if (!res.ok) {
    throw new Error(`${res.status} ${res.statusText} — ${path}`)
  }
  const text = await res.text()
  return (text ? JSON.parse(text) : undefined) as T
}

export const api = {
  listScripts: () => http<ScriptDto[]>('/api/scripts'),
  getScript: (id: string) => http<ScriptDto>(`/api/scripts/${id}`),
  createScript: (name: string, source: string) =>
    http<ScriptDto>('/api/scripts', { method: 'POST', body: JSON.stringify({ name, source }) }),
  updateScript: (id: string, name: string, source: string) =>
    http<ScriptDto>(`/api/scripts/${id}`, { method: 'PUT', body: JSON.stringify({ name, source }) }),
  deleteScript: (id: string) => http<void>(`/api/scripts/${id}`, { method: 'DELETE' }),

  listRuns: () => http<RunDto[]>('/api/runs'),
  getRun: (id: string) => http<RunDto>(`/api/runs/${id}`),
  startRun: (req: CreateRunRequest) =>
    http<RunDto>('/api/runs', { method: 'POST', body: JSON.stringify(req) }),
  stopRun: (id: string) => http<void>(`/api/runs/${id}/stop`, { method: 'POST' }),
  updatePeriod: (id: string, tickPeriodMs: number) =>
    http<void>(`/api/runs/${id}/period`, { method: 'POST', body: JSON.stringify({ tickPeriodMs }) }),
  metrics: (id: string) => http<MetricSnapshot[]>(`/api/runs/${id}/metrics`),
  logs: (id: string) => http<LogEntry[]>(`/api/runs/${id}/logs`),
  runHistory: (page = 0, size = 50) =>
    http<RunHistoryDto[]>(`/api/runs/history?page=${page}&size=${size}`),

  listFlows: () => http<FlowDto[]>('/api/flows'),
  getFlow: (id: string) => http<FlowDto>(`/api/flows/${id}`),
  createFlow: (name: string, graph: FlowGraphModel) =>
    http<FlowDto>('/api/flows', { method: 'POST', body: JSON.stringify({ name, graph }) }),
  deleteFlow: (id: string) => http<void>(`/api/flows/${id}`, { method: 'DELETE' }),
  deployFlow: (id: string) => http<DeployResponse>(`/api/flows/${id}/deploy`, { method: 'POST' }),
  stopFlow: (id: string) => http<void>(`/api/flows/${id}/stop`, { method: 'POST' }),

  listModules: () => http<ModuleDto[]>('/api/modules'),
  getModule: (id: string) => http<ModuleDto>(`/api/modules/${id}`),
  createModule: (name: string, version: string, specJson: string, source: string) =>
    http<ModuleDto>('/api/modules', {
      method: 'POST',
      body: JSON.stringify({ name, version, specJson, source }),
    }),

  dashboard: () => http<RunSummary[]>('/api/dashboard'),
}
