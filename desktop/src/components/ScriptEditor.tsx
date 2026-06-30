import Editor, { type OnMount } from '@monaco-editor/react'
import { useEffect, useRef, useState } from 'react'
import type { ScriptDto } from '../types'
import { RunLauncher, type RunConfig } from './RunLauncher'

interface Props {
  script: ScriptDto | null // null = 새 스크립트
  onSave: (name: string, source: string, id?: string) => void
  onDelete: (id: string) => void
  onRun: (id: string, config: RunConfig) => void
  onSaveAndRun: (name: string, source: string, id: string, config: RunConfig) => void
  onDirtyChange: (dirty: boolean) => void
}

export const DEFAULT_SOURCE = `import io.maestro.sdk.Script;

public class MyScript extends Script {
    @Override public void onStart() {
        ctx.log().info("start");
    }

    @Override public void onTick() {
        ctx.log().info("tick");
    }

    @Override public void onEnd() {
        ctx.log().info("end");
    }
}
`

// 에디터 버퍼가 마지막 저장 상태(또는 새 스크립트 기본값)와 다른지.
export function isDirty(name: string, source: string, script: ScriptDto | null): boolean {
  const baseName = script?.name ?? ''
  const baseSource = script?.source ?? DEFAULT_SOURCE
  return name !== baseName || source !== baseSource
}

export function ScriptEditor({
  script,
  onSave,
  onDelete,
  onRun,
  onSaveAndRun,
  onDirtyChange,
}: Props) {
  const [name, setName] = useState('')
  const [source, setSource] = useState(DEFAULT_SOURCE)

  useEffect(() => {
    if (script) {
      setName(script.name)
      setSource(script.source)
    } else {
      setName('')
      setSource(DEFAULT_SOURCE)
    }
  }, [script])

  const dirty = isDirty(name, source, script)

  useEffect(() => {
    onDirtyChange(dirty)
  }, [dirty, onDirtyChange])

  // 실행: 미저장 변경이 있으면 저장 후 실행(보이는 코드를 실행하도록 보장).
  const runWith = (cfg: RunConfig) => {
    if (!script) {
      return
    }
    if (dirty) {
      onSaveAndRun(name, source, script.id, cfg)
    } else {
      onRun(script.id, cfg)
    }
  }

  // 단축키 핸들러는 최신 상태를 봐야 하므로 ref로 보관(Monaco 명령은 마운트 시 1회 등록).
  const saveRef = useRef<() => void>(() => {})
  saveRef.current = () => {
    if (name.trim()) {
      onSave(name, source, script?.id)
    }
  }
  const runRef = useRef<() => void>(() => {})
  runRef.current = () => runWith({ tickPeriodMs: 1000 })

  const handleMount: OnMount = (editor, monaco) => {
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.KeyS, () => saveRef.current())
    editor.addCommand(monaco.KeyMod.CtrlCmd | monaco.KeyCode.Enter, () => runRef.current())
  }

  return (
    <div className="editor-pane">
      <div className="toolbar">
        <input
          className="name-input"
          placeholder="스크립트 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        {dirty && (
          <span className="dirty-dot" title="저장되지 않은 변경" aria-label="저장되지 않은 변경">
            ●
          </span>
        )}
        <button
          onClick={() => saveRef.current()}
          disabled={!name.trim()}
          title="저장 (⌘/Ctrl+S)"
        >
          {script ? '저장' : '생성'}
        </button>
        {script && <RunLauncher onRun={runWith} />}
        {script && (
          <button className="danger" onClick={() => onDelete(script.id)}>
            삭제
          </button>
        )}
      </div>
      <div className="editor-host">
        <Editor
          language="java"
          theme="vs-dark"
          value={source}
          onChange={(v) => setSource(v ?? '')}
          onMount={handleMount}
          options={{
            minimap: { enabled: false },
            fontSize: 13,
            automaticLayout: true,
            scrollBeyondLastLine: false,
            tabSize: 4,
          }}
        />
      </div>
    </div>
  )
}
