import { useEffect, useRef, useState } from 'react'
import { api } from '../api/client'
import { subscribe } from '../api/ws'
import type { LogEntry } from '../types'

export function LogsPanel({ runId }: { runId: string | null }) {
  const [logs, setLogs] = useState<LogEntry[]>([])
  const bottomRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    setLogs([])
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
    const unsub = subscribe<LogEntry>(runId, 'logs', (entry) =>
      setLogs((prev) => [...prev.slice(-499), entry]),
    )
    return () => {
      active = false
      unsub()
    }
  }, [runId])

  useEffect(() => {
    bottomRef.current?.scrollIntoView()
  }, [logs])

  return (
    <div className="logs-panel">
      <div className="panel-header">
        <h3>로그</h3>
      </div>
      <div className="log-lines">
        {logs.map((l, i) => (
          <div key={i} className={`log-line lvl-${l.level}`}>
            <span className="lvl">{l.level}</span> {l.message}
          </div>
        ))}
        {logs.length === 0 && <div className="muted">로그 없음</div>}
        <div ref={bottomRef} />
      </div>
    </div>
  )
}
