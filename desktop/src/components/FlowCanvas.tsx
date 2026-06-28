import { useCallback, useEffect, useState } from 'react'
import ReactFlow, {
  addEdge,
  Background,
  Controls,
  useEdgesState,
  useNodesState,
  type Connection,
  type Node,
} from 'reactflow'
import 'reactflow/dist/style.css'
import { api } from '../api/client'
import type { FlowDto, ScriptDto } from '../types'

interface NodeData {
  label: string
  scriptId: string
}

let nodeSeq = 1

export function FlowCanvas({ scripts }: { scripts: ScriptDto[] }) {
  const [nodes, setNodes, onNodesChange] = useNodesState<NodeData>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [flows, setFlows] = useState<FlowDto[]>([])
  const [name, setName] = useState('새 플로우')
  const [flowId, setFlowId] = useState<string | null>(null)
  const [pickScript, setPickScript] = useState('')
  const [status, setStatus] = useState('')

  const refreshFlows = useCallback(() => {
    api.listFlows().then(setFlows).catch(() => {})
  }, [])

  useEffect(() => {
    refreshFlows()
  }, [refreshFlows])

  const onConnect = useCallback(
    (c: Connection) => setEdges((eds) => addEdge({ ...c, label: 'out→in' }, eds)),
    [setEdges],
  )

  const addNode = () => {
    const script = scripts.find((s) => s.id === pickScript)
    if (!script) {
      return
    }
    const id = `n${nodeSeq++}`
    const node: Node<NodeData> = {
      id,
      position: { x: 80 + Math.random() * 240, y: 80 + Math.random() * 200 },
      data: { label: script.name, scriptId: script.id },
    }
    setNodes((ns) => [...ns, node])
  }

  const toGraph = () => ({
    nodes: nodes.map((n) => ({
      id: n.id,
      kind: 'SCRIPT' as const,
      refId: n.data.scriptId,
      params: {},
      tickPeriodMs: 1000,
    })),
    edges: edges.map((e) => ({
      fromNode: e.source,
      fromPort: 'out',
      toNode: e.target,
      toPort: 'in',
    })),
  })

  const save = async () => {
    try {
      const flow = await api.createFlow(name, toGraph())
      setFlowId(flow.id)
      refreshFlows()
      setStatus(`저장됨 (${flow.id.slice(0, 8)})`)
    } catch (e) {
      setStatus(`저장 실패: ${String(e)}`)
    }
  }

  const deploy = async () => {
    try {
      let id = flowId
      if (!id) {
        const f = await api.createFlow(name, toGraph())
        id = f.id
        setFlowId(id)
        refreshFlows()
      }
      const res = await api.deployFlow(id)
      setStatus(`배포됨: 노드 ${Object.keys(res.nodeRuns).length}개`)
    } catch (e) {
      setStatus(`배포 실패: ${String(e)}`)
    }
  }

  const loadFlow = async (id: string) => {
    try {
      const flow = await api.getFlow(id)
      setFlowId(flow.id)
      setName(flow.name)
      setNodes(
        flow.graph.nodes.map((n, i) => ({
          id: n.id,
          position: { x: 80 + i * 180, y: 120 },
          data: {
            label: scripts.find((s) => s.id === n.refId)?.name ?? n.refId,
            scriptId: n.refId,
          },
        })),
      )
      setEdges(
        flow.graph.edges.map((e, i) => ({
          id: `e${i}`,
          source: e.fromNode,
          target: e.toNode,
          label: 'out→in',
        })),
      )
    } catch (e) {
      setStatus(`불러오기 실패: ${String(e)}`)
    }
  }

  return (
    <div className="flow-canvas">
      <div className="toolbar">
        <input className="name-input" value={name} onChange={(e) => setName(e.target.value)} />
        <select value={pickScript} onChange={(e) => setPickScript(e.target.value)}>
          <option value="">스크립트 선택…</option>
          {scripts.map((s) => (
            <option key={s.id} value={s.id}>
              {s.name}
            </option>
          ))}
        </select>
        <button onClick={addNode} disabled={!pickScript}>
          + 노드
        </button>
        <button onClick={save}>저장</button>
        <button className="primary" onClick={deploy}>
          ▶ 배포
        </button>
        <select value={flowId ?? ''} onChange={(e) => e.target.value && loadFlow(e.target.value)}>
          <option value="">플로우 열기…</option>
          {flows.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
        <span className="muted small">{status}</span>
      </div>
      <div className="canvas-host">
        <ReactFlow
          nodes={nodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          fitView
        >
          <Background />
          <Controls />
        </ReactFlow>
      </div>
    </div>
  )
}
