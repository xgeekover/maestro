import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../api/ws'
import type { MetricSnapshot } from '../types'

function Metric({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="metric">
      <div className="m-val">{value}</div>
      <div className="m-label">{label}</div>
    </div>
  )
}

export function MetricsPanel({ runId }: { runId: string | null }) {
  const [latest, setLatest] = useState<MetricSnapshot | null>(null)

  useEffect(() => {
    setLatest(null)
    if (!runId) {
      return
    }
    let active = true
    api
      .metrics(runId)
      .then((ms) => {
        if (active && ms.length > 0) {
          setLatest(ms[ms.length - 1])
        }
      })
      .catch(() => {})
    const unsub = subscribe<MetricSnapshot>(runId, 'metrics', (m) => setLatest(m))
    return () => {
      active = false
      unsub()
    }
  }, [runId])

  return (
    <div className="metrics-panel">
      <div className="panel-header">
        <h3>메트릭</h3>
      </div>
      {!runId ? (
        <p className="muted">실행을 선택하세요.</p>
      ) : latest ? (
        <div className="metric-grid">
          <Metric label="tick" value={latest.tickCount} />
          <Metric label="error" value={latest.errorCount} />
          <Metric label="heap" value={`${(latest.heapUsedBytes / 1048576).toFixed(1)} MB`} />
          <Metric label="cpu" value={`${(latest.processCpuLoad * 100).toFixed(0)}%`} />
          <Metric label="uptime" value={`${(latest.uptimeMs / 1000).toFixed(0)}s`} />
          <Metric label="tick ms" value={latest.lastTickMs.toFixed(1)} />
        </div>
      ) : (
        <p className="muted">메트릭 대기 중…</p>
      )}
    </div>
  )
}
