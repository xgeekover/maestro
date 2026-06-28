import type { RunDto } from '../types'

interface Props {
  runs: RunDto[]
  selectedRunId: string | null
  onSelect: (id: string) => void
  onStop: (id: string) => void
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

function isLive(status: string): boolean {
  return status !== 'STOPPED' && status !== 'ERROR'
}

export function RunPanel({ runs, selectedRunId, onSelect, onStop }: Props) {
  return (
    <div className="run-panel">
      <div className="panel-header">
        <h3>실행 ({runs.length})</h3>
      </div>
      <ul className="run-list">
        {runs.map((r) => (
          <li
            key={r.runId}
            className={r.runId === selectedRunId ? 'selected' : ''}
            onClick={() => onSelect(r.runId)}
          >
            <span className="dot" style={{ background: STATUS_COLOR[r.status] ?? '#8b949e' }} />
            <span className="run-name">{r.scriptName}</span>
            <span className="run-status">
              {r.status}
              {r.restartCount > 0 ? ` ↻${r.restartCount}` : ''}
            </span>
            {isLive(r.status) && (
              <button
                className="mini"
                onClick={(e) => {
                  e.stopPropagation()
                  onStop(r.runId)
                }}
              >
                중지
              </button>
            )}
          </li>
        ))}
        {runs.length === 0 && <li className="muted">실행 중인 스크립트가 없습니다.</li>}
      </ul>
    </div>
  )
}
