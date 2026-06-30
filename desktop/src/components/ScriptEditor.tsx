import Editor from '@monaco-editor/react'
import { useEffect, useState } from 'react'
import type { ScriptDto } from '../types'
import { RunLauncher, type RunConfig } from './RunLauncher'

interface Props {
  script: ScriptDto | null // null = 새 스크립트
  onSave: (name: string, source: string, id?: string) => void
  onDelete: (id: string) => void
  onRun: (id: string, config: RunConfig) => void
}

const DEFAULT_SOURCE = `import io.maestro.sdk.Script;

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

export function ScriptEditor({ script, onSave, onDelete, onRun }: Props) {
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

  return (
    <div className="editor-pane">
      <div className="toolbar">
        <input
          className="name-input"
          placeholder="스크립트 이름"
          value={name}
          onChange={(e) => setName(e.target.value)}
        />
        <button onClick={() => onSave(name, source, script?.id)} disabled={!name.trim()}>
          {script ? '저장' : '생성'}
        </button>
        {script && <RunLauncher onRun={(cfg) => onRun(script.id, cfg)} />}
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
