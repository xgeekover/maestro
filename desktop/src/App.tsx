import { useCallback, useEffect, useMemo, useState } from 'react'
import { api } from './api/client'
import { ConfirmDialog, type ConfirmSpec } from './components/ConfirmDialog'
import { Dashboard } from './components/Dashboard'
import { FlowCanvas } from './components/FlowCanvas'
import { HistoryView } from './components/HistoryView'
import { LogsPanel } from './components/LogsPanel'
import { MetricsPanel } from './components/MetricsPanel'
import { RunControlBar } from './components/RunControlBar'
import { RunPanel } from './components/RunPanel'
import type { RunConfig } from './components/RunLauncher'
import { ScriptEditor } from './components/ScriptEditor'
import { ScriptList } from './components/ScriptList'
import type { RunDto, ScriptDto } from './types'

type View = 'scripts' | 'flows' | 'dashboard' | 'history'

const DISCARD_MSG = '저장하지 않은 변경이 있습니다. 버리고 이동할까요?'

export default function App() {
  const [scripts, setScripts] = useState<ScriptDto[]>([])
  const [selected, setSelected] = useState<ScriptDto | null>(null)
  const [runs, setRuns] = useState<RunDto[]>([])
  const [selectedRunId, setSelectedRunId] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [view, setView] = useState<View>('scripts')
  const [editorDirty, setEditorDirty] = useState(false)
  const [confirm, setConfirm] = useState<ConfirmSpec | null>(null)

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

  const doDelete = async (id: string) => {
    try {
      await api.deleteScript(id)
      setSelected(null)
      setEditorDirty(false)
      refreshScripts()
    } catch (e) {
      setError(String(e))
    }
  }

  const onRun = async (scriptId: string, config: RunConfig) => {
    try {
      const run = await api.startRun({ scriptId, ...config })
      setSelectedRunId(run.runId)
      refreshRuns()
      setError(null)
    } catch (e) {
      setError(String(e))
    }
  }

  // 저장 후 실행: 미저장 버퍼를 먼저 영속화하고, 그 스크립트로 실행.
  const onSaveAndRun = async (name: string, source: string, id: string, config: RunConfig) => {
    try {
      const saved = await api.updateScript(id, name, source)
      refreshScripts()
      setSelected(saved)
      const run = await api.startRun({ scriptId: saved.id, ...config })
      setSelectedRunId(run.runId)
      refreshRuns()
      setError(null)
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

  const onUpdatePeriod = async (runId: string, tickPeriodMs: number) => {
    try {
      await api.updatePeriod(runId, tickPeriodMs)
      setError(null)
    } catch (e) {
      setError(String(e))
    }
  }

  const selectedRun = useMemo(
    () => runs.find((r) => r.runId === selectedRunId) ?? null,
    [runs, selectedRunId],
  )

  // 미저장 변경이 있으면 이동 전에 확인을 받는다(스크립트 전환·새로 만들기·탭 이동).
  const guardDiscard = (action: () => void) => {
    if (editorDirty) {
      setConfirm({ message: DISCARD_MSG, confirmLabel: '버리고 이동', danger: true, action })
    } else {
      action()
    }
  }

  const selectScript = (next: ScriptDto | null) => guardDiscard(() => setSelected(next))

  const changeView = (next: View) => {
    if (next === view) {
      return
    }
    if (view === 'scripts' && next !== 'scripts') {
      guardDiscard(() => {
        setEditorDirty(false)
        setView(next)
      })
    } else {
      setView(next)
    }
  }

  const requestDelete = (id: string) => {
    const name = scripts.find((s) => s.id === id)?.name ?? '이 스크립트'
    setConfirm({
      message: `'${name}' 스크립트를 삭제합니다. 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      danger: true,
      action: () => doDelete(id),
    })
  }

  const tabClass = (v: View) => (view === v ? 'tab active' : 'tab')

  return (
    <div className="app">
      <header className="app-header">
        <span className="title">🎼 Maestro</span>
        <nav className="tabs">
          <button className={tabClass('scripts')} onClick={() => changeView('scripts')}>
            스크립트
          </button>
          <button className={tabClass('flows')} onClick={() => changeView('flows')}>
            플로우
          </button>
          <button className={tabClass('dashboard')} onClick={() => changeView('dashboard')}>
            대시보드
          </button>
          <button className={tabClass('history')} onClick={() => changeView('history')}>
            이력
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
              onSelect={selectScript}
              onNew={() => selectScript(null)}
            />
          </aside>
          <main className="center">
            <ScriptEditor
              script={selected}
              onSave={onSave}
              onDelete={requestDelete}
              onRun={onRun}
              onSaveAndRun={onSaveAndRun}
              onDirtyChange={setEditorDirty}
            />
          </main>
          <aside className="right">
            <RunPanel
              runs={runs}
              selectedRunId={selectedRunId}
              onSelect={setSelectedRunId}
              onStop={onStop}
            />
            <RunControlBar run={selectedRun} onUpdatePeriod={onUpdatePeriod} />
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
      {view === 'history' && (
        <div className="app-body-flow">
          <HistoryView />
        </div>
      )}
      <ConfirmDialog spec={confirm} onClose={() => setConfirm(null)} />
    </div>
  )
}
