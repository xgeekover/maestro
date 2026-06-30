import { useState } from 'react'
import type { RunDto } from '../types'

interface Props {
  run: RunDto | null
  onUpdatePeriod: (runId: string, tickPeriodMs: number) => void
}

const STATUS_COLOR: Record<string, string> = {
  PENDING: '#8b949e',
  COMPILING: '#d29922',
  STARTING: '#d29922',
  RUNNING: '#3fb950',
  STOPPING: '#d29922',
  STOPPED: '#8b949e',
  ERROR: '#f85149',
}

export function RunControlBar({ run, onUpdatePeriod }: Props) {
  const [periodMs, setPeriodMs] = useState('')

  if (!run) {
    return null
  }
  // 주기 변경은 엔진 tick 루프가 도는 RUNNING 상태에서만 의미가 있다.
  const live = run.status === 'RUNNING'
  const apply = () => {
    const p = Number(periodMs)
    if (Number.isFinite(p) && p > 0) {
      onUpdatePeriod(run.runId, p)
    }
  }
  return (
    <div className="run-control-bar">
      <span className="dot" style={{ background: STATUS_COLOR[run.status] ?? '#8b949e' }} />
      <span className="rcb-status">{run.status}</span>
      <span className="muted small">
        pid {run.pid}
        {run.restartCount > 0 ? ` · ↻${run.restartCount}` : ''}
      </span>
      {live && (
        <span className="period-ctl">
          <input
            aria-label="새 주기(ms)"
            placeholder="주기(ms)"
            value={periodMs}
            onChange={(e) => setPeriodMs(e.target.value)}
            inputMode="numeric"
            onKeyDown={(e) => {
              if (e.key === 'Enter') {
                apply()
              }
            }}
          />
          <button className="mini" onClick={apply} disabled={!periodMs.trim()}>
            주기 적용
          </button>
        </span>
      )}
    </div>
  )
}
