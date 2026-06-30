import { useEffect, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../api/ws'
import type { MetricSnapshot } from '../types'
import { Sparkline } from './Sparkline'

const MAX_POINTS = 60

export interface MetricSeries {
  cpu: number[]
  heap: number[]
  tick: number[]
}

const EMPTY: MetricSeries = { cpu: [], heap: [], tick: [] }

// 스냅샷 한 건을 시계열 3종으로 사상.
function point(m: MetricSnapshot): { cpu: number; heap: number; tick: number } {
  return { cpu: m.processCpuLoad * 100, heap: m.heapUsedBytes / 1048576, tick: m.lastTickMs }
}

// 초기 조회 결과(배열)로 최근 max개 시계열 시드.
export function seedSeries(ms: MetricSnapshot[], max = MAX_POINTS): MetricSeries {
  const recent = ms.slice(-max).map(point)
  return {
    cpu: recent.map((p) => p.cpu),
    heap: recent.map((p) => p.heap),
    tick: recent.map((p) => p.tick),
  }
}

// 새 스냅샷을 시계열에 추가(max 초과분은 앞에서 버림).
export function pushSeries(prev: MetricSeries, m: MetricSnapshot, max = MAX_POINTS): MetricSeries {
  const p = point(m)
  return {
    cpu: [...prev.cpu, p.cpu].slice(-max),
    heap: [...prev.heap, p.heap].slice(-max),
    tick: [...prev.tick, p.tick].slice(-max),
  }
}

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
  const [series, setSeries] = useState<MetricSeries>(EMPTY)

  useEffect(() => {
    setLatest(null)
    setSeries(EMPTY)
    if (!runId) {
      return
    }
    let active = true
    api
      .metrics(runId)
      .then((ms) => {
        if (active && ms.length > 0) {
          setLatest(ms[ms.length - 1])
          setSeries(seedSeries(ms))
        }
      })
      .catch(() => {})
    const unsub = subscribe<MetricSnapshot>(runId, 'metrics', (m) => {
      setLatest(m)
      setSeries((prev) => pushSeries(prev, m))
    })
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
        <>
          <div className="metric-grid">
            <Metric label="tick" value={latest.tickCount} />
            <Metric label="error" value={latest.errorCount} />
            <Metric label="heap" value={`${(latest.heapUsedBytes / 1048576).toFixed(1)} MB`} />
            <Metric label="cpu" value={`${(latest.processCpuLoad * 100).toFixed(0)}%`} />
            <Metric label="uptime" value={`${(latest.uptimeMs / 1000).toFixed(0)}s`} />
            <Metric label="tick ms" value={latest.lastTickMs.toFixed(1)} />
          </div>
          <div className="metric-charts">
            <div className="mc">
              <div className="dc-label">CPU %</div>
              <Sparkline values={series.cpu} max={100} color="#f0883e" />
            </div>
            <div className="mc">
              <div className="dc-label">Heap MB</div>
              <Sparkline values={series.heap} color="#58a6ff" />
            </div>
            <div className="mc">
              <div className="dc-label">tick ms</div>
              <Sparkline values={series.tick} color="#3fb950" />
            </div>
          </div>
        </>
      ) : (
        <p className="muted">메트릭 대기 중…</p>
      )}
    </div>
  )
}
