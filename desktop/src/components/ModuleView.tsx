import Editor from '@monaco-editor/react'
import { useCallback, useEffect, useState } from 'react'
import { api } from '../api/client'
import type { ModuleDto } from '../types'
import { ConfirmDialog, type ConfirmSpec } from './ConfirmDialog'

const DEFAULT_MODULE_SOURCE = `import io.maestro.sdk.Script;

// 모듈도 스크립트와 동일하게 io.maestro.sdk.Script를 상속한다.
// 플로우에서 재사용되는 버전 관리 단위(name@version).
public class MyModule extends Script {
    @Override public void onStart() {
        ctx.log().info("module start");
    }

    @Override public void onTick() {
        // 입력을 받아 처리하고 out 포트로 emit
        ctx.emit("out", ctx.get("in", ""));
    }
}
`

const DEFAULT_SPEC = `{
  "in": ["in"],
  "out": ["out"]
}`

// specJson이 비었으면 허용(백엔드 기본 "{}"), 있으면 유효한 JSON이어야 한다.
export function specJsonError(text: string): string | null {
  const t = text.trim()
  if (!t) {
    return null
  }
  try {
    JSON.parse(t)
    return null
  } catch {
    return 'specJson이 올바른 JSON이 아닙니다'
  }
}

// 생성에 필요한 필수 입력이 채워졌는지.
export function moduleFormReady(name: string, version: string, source: string): boolean {
  return name.trim() !== '' && version.trim() !== '' && source.trim() !== ''
}

export function ModuleView({ onModulesChanged }: { onModulesChanged?: () => void }) {
  const [modules, setModules] = useState<ModuleDto[]>([])
  const [selectedId, setSelectedId] = useState<string | null>(null)
  const [name, setName] = useState('')
  const [version, setVersion] = useState('1.0.0')
  const [specJson, setSpecJson] = useState(DEFAULT_SPEC)
  const [source, setSource] = useState(DEFAULT_MODULE_SOURCE)
  const [msg, setMsg] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmSpec | null>(null)

  const refresh = useCallback(() => {
    api.listModules().then(setModules).catch(() => {})
  }, [])

  useEffect(() => {
    refresh()
  }, [refresh])

  const selectModule = (m: ModuleDto) => {
    setSelectedId(m.id)
    setName(m.name)
    setVersion(m.version)
    setSpecJson(m.specJson || DEFAULT_SPEC)
    setSource(m.source)
    setMsg(null)
  }

  const newModule = () => {
    setSelectedId(null)
    setName('')
    setVersion('1.0.0')
    setSpecJson(DEFAULT_SPEC)
    setSource(DEFAULT_MODULE_SOURCE)
    setMsg(null)
  }

  const specErr = specJsonError(specJson)
  const canSubmit = moduleFormReady(name, version, source) && !specErr

  const create = async () => {
    try {
      const created = await api.createModule(name, version, specJson.trim() || '{}', source)
      setSelectedId(created.id)
      refresh()
      onModulesChanged?.()
      setMsg(`생성됨: ${created.name}@${created.version}`)
    } catch (e) {
      setMsg(`생성 실패: ${String(e)}`)
    }
  }

  const save = async () => {
    if (!selectedId) {
      return
    }
    try {
      const saved = await api.updateModule(selectedId, name, version, specJson.trim() || '{}', source)
      refresh()
      onModulesChanged?.()
      setMsg(`저장됨: ${saved.name}@${saved.version}`)
    } catch (e) {
      setMsg(`저장 실패: ${String(e)}`)
    }
  }

  const requestDelete = () => {
    if (!selectedId) {
      return
    }
    const id = selectedId
    setConfirm({
      message: `'${name}@${version}' 모듈을 삭제합니다. 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      danger: true,
      action: async () => {
        try {
          await api.deleteModule(id)
          newModule()
          refresh()
          onModulesChanged?.()
          setMsg('삭제됨')
        } catch (e) {
          setMsg(`삭제 실패: ${String(e)}`)
        }
      },
    })
  }

  return (
    <div className="module-view">
      <aside className="sidebar">
        <div className="panel-header">
          <h3>모듈</h3>
          <button className="mini" onClick={newModule}>
            + 새로
          </button>
        </div>
        <ul className="script-list">
          {modules.map((m) => (
            <li
              key={m.id}
              className={m.id === selectedId ? 'selected' : ''}
              onClick={() => selectModule(m)}
            >
              <span className="run-name">
                {m.name}
                <span className="muted small"> @{m.version}</span>
              </span>
            </li>
          ))}
          {modules.length === 0 && <li className="muted">모듈이 없습니다.</li>}
        </ul>
      </aside>
      <main className="center module-editor">
        <div className="toolbar mod-toolbar">
          <input
            className="name-input"
            placeholder="모듈 이름"
            aria-label="모듈 이름"
            value={name}
            onChange={(e) => setName(e.target.value)}
          />
          <input
            className="ver-input"
            placeholder="버전(semver)"
            aria-label="버전"
            value={version}
            onChange={(e) => setVersion(e.target.value)}
          />
          {selectedId ? (
            <>
              <button className="primary" onClick={save} disabled={!canSubmit} title="제자리 저장(수정)">
                저장
              </button>
              <button className="danger" onClick={requestDelete}>
                삭제
              </button>
            </>
          ) : (
            <button className="primary" onClick={create} disabled={!canSubmit} title="새 모듈 생성">
              생성
            </button>
          )}
          {msg && <span className="muted small">{msg}</span>}
        </div>
        <div className="mod-spec">
          <label>
            포트/파라미터 스펙 (specJson)
            <textarea
              aria-label="specJson"
              rows={4}
              value={specJson}
              onChange={(e) => setSpecJson(e.target.value)}
            />
          </label>
          {specErr && <span className="mod-spec-err">⚠ {specErr}</span>}
          <span className="muted small">
            {selectedId
              ? '선택한 모듈을 제자리 수정합니다. 새 모듈은 "+ 새로".'
              : '새 모듈을 생성합니다. 기존 모듈은 왼쪽에서 선택해 수정/삭제.'}
          </span>
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
      </main>
      <ConfirmDialog spec={confirm} onClose={() => setConfirm(null)} />
    </div>
  )
}
