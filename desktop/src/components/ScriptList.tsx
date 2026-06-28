import type { ScriptDto } from '../types'

interface Props {
  scripts: ScriptDto[]
  selectedId: string | null
  onSelect: (s: ScriptDto) => void
  onNew: () => void
}

export function ScriptList({ scripts, selectedId, onSelect, onNew }: Props) {
  return (
    <div className="script-list">
      <div className="panel-header">
        <h3>스크립트</h3>
        <button className="mini" onClick={onNew}>+ 새로</button>
      </div>
      <ul>
        {scripts.map((s) => (
          <li
            key={s.id}
            className={s.id === selectedId ? 'selected' : ''}
            onClick={() => onSelect(s)}
          >
            {s.name}
          </li>
        ))}
        {scripts.length === 0 && <li className="muted">스크립트가 없습니다.</li>}
      </ul>
    </div>
  )
}
