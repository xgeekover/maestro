import { useEffect, useMemo, useRef, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../api/ws'
import type { LogEntry } from '../types'

export type LevelFilter = 'ALL' | 'INFO' | 'WARN' | 'ERROR'

const RANK: Record<string, number> = { DEBUG: 0, INFO: 1, WARN: 2, ERROR: 3 }

// 최소 레벨 이상 + 검색어(대소문자 무시, 메시지 부분일치)로 로그 필터.
export function filterLogs(logs: LogEntry[], minLevel: LevelFilter, query: string): LogEntry[] {
  const min = minLevel === 'ALL' ? -1 : (RANK[minLevel] ?? 0)
  const q = query.trim().toLowerCase()
  return logs.filter(
    (l) => (RANK[l.level] ?? 1) >= min && (!q || l.message.toLowerCase().includes(q)),
  )
}

export function LogsPanel({ runId }: { runId: string | null }) {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const [level, setLevel] = useState<LevelFilter>('ALL')
  const [query, setQuery] = useState('')
  const [follow, setFollow] = useState(true)
  const [reconnecting, setReconnecting] = useState(false)
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setLogs([])
    setReconnecting(false)
    if (!runId) {
      return
    }
    let active = true
    api
      .logs(runId)
      .then((initial) => {
        if (active) {
          setLogs(initial)
        }
      })
      .catch(() => {})
    const unsub = subscribe<LogEntry>(
      runId,
      'logs',
      (entry) => setLogs((prev) => [...prev.slice(-499), entry]),
      (state) => setReconnecting(state === 'reconnecting'),
    )
    return () => {
      active = false
      unsub()
    }
  }, [runId])

  const visible = useMemo(() => filterLogs(logs, level, query), [logs, level, query])

  useEffect(() => {
    if (follow) {
      bottomRef.current?.scrollIntoView()
    }
  }, [visible, follow])

  const copy = () => {
    const text = visible.map((l) => `${l.level} ${l.message}`).join('\n')
    navigator.clipboard?.writeText(text).catch(() => {})
  }

  return (
    <div className="logs-panel">
      <div className="panel-header logs-header">
        <h3>로그</h3>
        {reconnecting && <span className="reconnect-hint small">● 재연결 중…</span>}
        <span className="muted small">
          {visible.length}
          {visible.length !== logs.length ? `/${logs.length}` : ''}
        </span>
        <span style={{ flex: 1 }} />
        <select
          aria-label="레벨 필터"
          className="log-filter"
          value={level}
          onChange={(e) => setLevel(e.target.value as LevelFilter)}
        >
          <option value="ALL">전체</option>
          <option value="INFO">INFO+</option>
          <option value="WARN">WARN+</option>
          <option value="ERROR">ERROR</option>
        </select>
        <input
          aria-label="로그 검색"
          className="log-search"
          placeholder="검색…"
          value={query}
          onChange={(e) => setQuery(e.target.value)}
        />
        <button
          className={follow ? 'mini active' : 'mini'}
          title="자동 스크롤"
          onClick={() => setFollow((f) => !f)}
        >
          {follow ? '⏸' : '▶'}
        </button>
        <button className="mini" title="복사" onClick={copy} disabled={visible.length === 0}>
          ⧉
        </button>
        <button className="mini" title="지우기" onClick={() => setLogs([])} disabled={logs.length === 0}>
          ✕
        </button>
      </div>
      <div className="log-lines">
        {visible.map((l, i) => (
          <div key={i} className={`log-line lvl-${l.level}`}>
            <span className="lvl">{l.level}</span> {l.message}
          </div>
        ))}
        {visible.length === 0 && (
          <div className="muted">{logs.length === 0 ? '로그 없음' : '일치하는 로그 없음'}</div>
        )}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
