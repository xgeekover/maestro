import { useEffect, useState } from 'react'
import { api } from '../api/client'
import type { RunSummary } from '../types'
import { LogsPanel } from './LogsPanel'
import { Sparkline } from './Sparkline'

const MAX_POINTS = 60

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#8b949e',
  COMPILING: '#d29922',
  STARTING: '#d29922',
  RUNNING: '#3fb950',
  STOPPING: '#d29922',
  STOPPED: '#8b949e',
  ERROR: '#f85149',
}

interface Series {
  cpu: number[]
  heap: number[]
}

export function Dashboard() {
  const [summaries, setSummaries] = useState<RunSummary[]>([])
  const [series, setSeries] = useState<Record<string, Series>>({})
  const [selected, setSelected] = useState<string | null>(null)

  useEffect(() => {
    let active = true
    const poll = async () => {
      try {
        const data = await api.dashboard()
        if (!active) {
          return
        }
        setSummaries(data)
        setSeries((prev) => {
          const next: Record<string, Series> = { ...prev }
          for (const s of data) {
            if (!s.latest) {
              continue
            }
            const e = next[s.run.runId] ?? { cpu: [], heap: [] }
            next[s.run.runId] = {
              cpu: [...e.cpu, s.latest.processCpuLoad * 100].slice(-MAX_POINTS),
              heap: [...e.heap, s.latest.heapUsedBytes / 1048576].slice(-MAX_POINTS),
            }
          }
          return next
        })
      } catch {
        // 무시
      }
    }
    poll()
    const id = setInterval(poll, 1000)
    return () => {
      active = false
      clearInterval(id)
    }
  }, [])

  return (
    <div className="dashboard">
      <div className="dash-grid">
        {summaries.map((s) => {
          const ser = series[s.run.runId]
          const m = s.latest
          return (
            <div
              key={s.run.runId}
              className={`dash-card ${s.run.runId === selected ? 'selected' : ''}`}
              onClick={() => setSelected(s.run.runId)}
            >
              <div className="dc-head">
                <span className="dot" style={{ background: STATUS_COLOR[s.run.status] ?? '#8b949e' }} />
                <span className="dc-name">{s.run.scriptName}</span>
                <span className="dc-status">
                  {s.run.status}
                  {s.run.restartCount > 0 ? ` ↻${s.run.restartCount}` : ''}
                </span>
              </div>
              <div className="dc-charts">
                <div className="dc-chart">
                  <div className="dc-label">CPU {m ? `${(m.processCpuLoad * 100).toFixed(0)}%` : '—'}</div>
                  <Sparkline values={ser?.cpu ?? []} max={100} color="#f0883e" />
                </div>
                <div className="dc-chart">
                  <div className="dc-label">Heap {m ? `${(m.heapUsedBytes / 1048576).toFixed(0)}MB` : '—'}</div>
                  <Sparkline values={ser?.heap ?? []} color="#58a6ff" />
                </div>
              </div>
              <div className="dc-stats">
                <span>tick {m?.tickCount ?? 0}</span>
                <span>err {m?.errorCount ?? 0}</span>
                <span>up {m ? `${(m.uptimeMs / 1000).toFixed(0)}s` : '—'}</span>
              </div>
            </div>
          )
        })}
        {summaries.length === 0 && (
          <div className="muted" style={{ padding: 16 }}>
            실행 중인 프로세스가 없습니다.
          </div>
        )}
      </div>
      <div className="dash-logs">
        <LogsPanel runId={selected} />
      </div>
    </div>
  )
}
