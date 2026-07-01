import { Handle, Position, type NodeProps } from 'reactflow'
import type { NodeKind } from '../types'
import type { Ports } from './flowGraph'

export interface FlowNodeCardData {
  name: string
  kind: NodeKind
  refId: string
  tickPeriodMs: number
  params: Record<string, string>
  ports: Ports
  status?: string
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

// 포트 개수에 맞춰 세로로 균등 배치(1개면 중앙).
function offset(index: number, count: number): string {
  return `${((index + 1) / (count + 1)) * 100}%`
}

export function FlowNodeCard({ data, selected }: NodeProps<FlowNodeCardData>) {
  const color = data.status ? STATUS_COLOR[data.status] ?? '#8b949e' : undefined
  return (
    <div
      className={`flow-node${selected ? ' selected' : ''}`}
      style={color ? { borderColor: color, boxShadow: `0 0 0 1px ${color}` } : undefined}
    >
      {data.ports.in.map((p, i) => (
        <Handle
          key={`in-${p}`}
          id={p}
          type="target"
          position={Position.Left}
          style={{ top: offset(i, data.ports.in.length) }}
        >
          <span className="port-label port-in">{p}</span>
        </Handle>
      ))}
      <div className="fn-title">{data.kind === 'MODULE' ? '📦 ' : ''}{data.name}</div>
      {data.status && (
        <div className="fn-status" style={{ color }}>
          {data.status}
        </div>
      )}
      {data.ports.out.map((p, i) => (
        <Handle
          key={`out-${p}`}
          id={p}
          type="source"
          position={Position.Right}
          style={{ top: offset(i, data.ports.out.length) }}
        >
          <span className="port-label port-out">{p}</span>
        </Handle>
      ))}
    </div>
  )
}
