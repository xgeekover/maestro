import { useCallback, useEffect, useState } from 'react'
import { api } from './api/client'
import { Dashboard } from './components/Dashboard'
import { FlowCanvas } from './components/FlowCanvas'
import { LogsPanel } from './components/LogsPanel'
import { MetricsPanel } from './components/MetricsPanel'
import { RunPanel } from './components/RunPanel'
import { ScriptEditor } from './components/ScriptEditor'
import { ScriptList } from './components/ScriptList'
import type { RunDto, ScriptDto } from './types'

type View = 'scripts' | 'flows' | 'dashboard'

export default function App() {
  const [scripts, setScripts] = useState<ScriptDto[]>([])
  const [selected, setSelected] = useState<ScriptDto | null>(null)
  const [runs, setRuns] = useState<RunDto[]>([])
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [view, setView] = useState<View>('scripts')

  const refreshScripts = useCallback(() => {
    api.listScripts().then(setScripts).catch((e) => setError(String(e)))
  }, [])

  const refreshRuns = useCallback(() => {
    api.listRuns().then(setRuns).catch(() => {})
  }, [])

  useEffect(() => {
    refreshScripts()
  }, [refreshScripts])

  useEffect(() => {
    refreshRuns()
    const id = setInterval(refreshRuns, 1000)
    return () => clearInterval(id)
  }, [refreshRuns])

  const onSave = async (name: string, source: string, id?: string) => {
    try {
      const saved = id
        ? await api.updateScript(id, name, source)
        : await api.createScript(name, source)
      refreshScripts()
      setSelected(saved)
      setError(null)
    } catch (e) {
      setError(String(e))
    }
  }

  const onDelete = async (id: string) => {
    try {
      await api.deleteScript(id)
      setSelected(null)
      refreshScripts()
    } catch (e) {
      setError(String(e))
    }
  }

  const onRun = async (scriptId: string) => {
    try {
      const run = await api.startRun({ scriptId, tickPeriodMs: 1000 })
      setSelectedRunId(run.runId)
      refreshRuns()
    } catch (e) {
      setError(String(e))
    }
  }

  const onStop = async (runId: string) => {
    try {
      await api.stopRun(runId)
      refreshRuns()
    } catch {
      // 무시
    }
  }

  return (
    <div className="app">
      <header className="app-header">
        <span className="title">🎼 Maestro</span>
        <nav className="tabs">
          <button
            className={view === 'scripts' ? 'tab active' : 'tab'}
            onClick={() => setView('scripts')}
          >
            스크립트
          </button>
          <button
            className={view === 'flows' ? 'tab active' : 'tab'}
            onClick={() => setView('flows')}
          >
            플로우
          </button>
          <button
            className={view === 'dashboard' ? 'tab active' : 'tab'}
            onClick={() => setView('dashboard')}
          >
            대시보드
          </button>
        </nav>
        {error && (
          <span className="err" title={error}>
            ⚠ {error}
          </span>
        )}
      </header>
      {view === 'scripts' && (
        <div className="app-body">
          <aside className="sidebar">
            <ScriptList
              scripts={scripts}
              selectedId={selected?.id ?? null}
              onSelect={setSelected}
              onNew={() => setSelected(null)}
            />
          </aside>
          <main className="center">
            <ScriptEditor script={selected} onSave={onSave} onDelete={onDelete} onRun={onRun} />
          </main>
          <aside className="right">
            <RunPanel
              runs={runs}
              selectedRunId={selectedRunId}
              onSelect={setSelectedRunId}
              onStop={onStop}
            />
            <MetricsPanel runId={selectedRunId} />
            <LogsPanel runId={selectedRunId} />
          </aside>
        </div>
      )}
      {view === 'flows' && (
        <div className="app-body-flow">
          <FlowCanvas scripts={scripts} />
        </div>
      )}
      {view === 'dashboard' && (
        <div className="app-body-flow">
          <Dashboard />
        </div>
      )}
    </div>
  )
}
