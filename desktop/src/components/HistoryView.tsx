import { Fragment, useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { RunHistoryDto } from '../types'

const PAGE_SIZE = 50

const STATUS_COLOR: Record<string, string> = {
  STOPPED: '#8b949e',
  ERROR: '#f85149',
  COMPLETED: '#3fb950',
}

// ISO 타임스탬프를 'YYYY-MM-DD HH:MM:SS'로 축약. 비결정적 Date 사용 안 함.
function fmt(ts: string | null): string {
  if (!ts) {
    return '—'
  }
  const m = /^(\d{4}-\d{2}-\d{2})[T ](\d{2}:\d{2}:\d{2})/.exec(ts)
  return m ? `${m[1]} ${m[2]}` : ts
}

export function HistoryView() {
  const [rows, setRows] = useState<RunHistoryDto[]>([])
  const [page, setPage] = useState(0)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [expanded, setExpanded] = useState<string | null>(null)

  const load = useCallback((p: number) => {
    setLoading(true)
    api
      .runHistory(p, PAGE_SIZE)
      .then((r) => {
        setRows(r)
        setError(null)
      })
      .catch((e) => setError(String(e)))
      .finally(() => setLoading(false))
  }, [])

  useEffect(() => {
    load(page)
  }, [load, page])

  const hasNext = rows.length === PAGE_SIZE

  return (
    <div className="history-view">
      <div className="toolbar">
        <strong>실행 이력</strong>
        <span className="muted small">종료된 실행 (최신순)</span>
        <button className="mini" onClick={() => load(page)} disabled={loading}>
          ↻ 새로고침
        </button>
        <span style={{ flex: 1 }} />
        <button className="mini" onClick={() => setPage((p) => Math.max(0, p - 1))} disabled={page === 0}>
          ← 이전
        </button>
        <span className="muted small">{page + 1} 페이지</span>
        <button className="mini" onClick={() => setPage((p) => p + 1)} disabled={!hasNext}>
          다음 →
        </button>
      </div>
      {error && <div className="hv-error">⚠ {error}</div>}
      <div className="hv-table-host">
        <table className="hv-table">
          <thead>
            <tr>
              <th>스크립트</th>
              <th>상태</th>
              <th>시작</th>
              <th>종료</th>
              <th>재시작</th>
              <th>출처</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((r) => (
              <Fragment key={r.runId}>
                <tr
                  className={r.lastError ? 'has-error' : ''}
                  onClick={() => setExpanded((e) => (e === r.runId ? null : r.runId))}
                >
                  <td>{r.scriptName}</td>
                  <td>
                    <span
                      className="dot"
                      style={{ background: STATUS_COLOR[r.status] ?? '#8b949e' }}
                    />
                    {r.status}
                  </td>
                  <td className="mono">{fmt(r.startedAt)}</td>
                  <td className="mono">{fmt(r.endedAt)}</td>
                  <td>{r.restartCount > 0 ? `↻${r.restartCount}` : '—'}</td>
                  <td className="muted small">{r.flowId ? `flow:${r.nodeId ?? ''}` : 'script'}</td>
                </tr>
                {expanded === r.runId && r.lastError && (
                  <tr className="hv-detail">
                    <td colSpan={6}>
                      <pre className="hv-error-detail">{r.lastError}</pre>
                    </td>
                  </tr>
                )}
              </Fragment>
            ))}
          </tbody>
        </table>
        {rows.length === 0 && !loading && <div className="muted hv-empty">이력이 없습니다.</div>}
        {loading && <div className="muted hv-empty">불러오는 중…</div>}
      </div>
    </div>
  )
}
