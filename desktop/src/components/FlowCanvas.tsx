import { useCallback, useEffect, useMemo, useState } from 'react'
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
import type { FlowDto, ModuleDto, NodeKind, RunDto, ScriptDto } from '../types'
import { ConfirmDialog, type ConfirmSpec } from './ConfirmDialog'
import { buildGraph, mapNodeStatuses, paramsToText, refLabel } from './flowGraph'
import { parseParams } from './RunLauncher'

interface NodeData {
  label: string
  name: string
  kind: NodeKind
  refId: string
  tickPeriodMs: number
  params: Record<string, string>
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

let nodeSeq = 1

export function FlowCanvas({
  scripts,
  modules,
  runs,
}: {
  scripts: ScriptDto[]
  modules: ModuleDto[]
  runs: RunDto[]
}) {
  const [nodes, setNodes, onNodesChange] = useNodesState<NodeData>([])
  const [edges, setEdges, onEdgesChange] = useEdgesState([])
  const [flows, setFlows] = useState<FlowDto[]>([])
  const [name, setName] = useState('새 플로우')
  const [flowId, setFlowId] = useState<string | null>(null)
  const [pick, setPick] = useState('')
  const [status, setStatus] = useState('')
  const [deployedRuns, setDeployedRuns] = useState<Record<string, string>>({})
  const [selectedNodeId, setSelectedNodeId] = useState<string | null>(null)
  const [confirm, setConfirm] = useState<ConfirmSpec | null>(null)
  // 설정 패널 입력 버퍼(타이핑 중 원문 보존; 파싱값은 노드 데이터에 반영)
  const [periodDraft, setPeriodDraft] = useState('')
  const [paramsDraft, setParamsDraft] = useState('')

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

  // pick 값은 "script:<id>" 또는 "module:<id>" 로 인코딩.
  const addNode = () => {
    const sep = pick.indexOf(':')
    if (sep < 0) {
      return
    }
    const kind: NodeKind = pick.slice(0, sep) === 'module' ? 'MODULE' : 'SCRIPT'
    const refId = pick.slice(sep + 1)
    const label = refLabel(kind, refId, scripts, modules)
    const id = `n${nodeSeq++}`
    const node: Node<NodeData> = {
      id,
      position: { x: 80 + Math.random() * 240, y: 80 + Math.random() * 200 },
      data: { label, name: label, kind, refId, tickPeriodMs: 1000, params: {} },
    }
    setNodes((ns) => [...ns, node])
  }

  // 노드별 상태(배포 결과 + 현재 실행 목록)
  const statuses = useMemo(() => mapNodeStatuses(deployedRuns, runs), [deployedRuns, runs])

  // 렌더용 노드: 상태 색·라벨을 입힌 파생 배열(기준 nodes는 그대로 관리).
  const displayNodes = useMemo(
    () =>
      nodes.map((n) => {
        const st = statuses[n.id]
        return {
          ...n,
          data: { ...n.data, label: st ? `${n.data.name} · ${st}` : n.data.name },
          style: st
            ? { borderColor: STATUS_COLOR[st] ?? '#8b949e', boxShadow: `0 0 0 1px ${STATUS_COLOR[st] ?? '#8b949e'}` }
            : undefined,
        }
      }),
    [nodes, statuses],
  )

  const save = async () => {
    try {
      const flow = await api.createFlow(name, buildGraph(nodes, edges))
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
        const f = await api.createFlow(name, buildGraph(nodes, edges))
        id = f.id
        setFlowId(id)
        refreshFlows()
      }
      const res = await api.deployFlow(id)
      setDeployedRuns(res.nodeRuns)
      setStatus(`배포됨: 노드 ${Object.keys(res.nodeRuns).length}개`)
    } catch (e) {
      setStatus(`배포 실패: ${String(e)}`)
    }
  }

  const stop = async () => {
    if (!flowId) {
      return
    }
    try {
      await api.stopFlow(flowId)
      setDeployedRuns({})
      setStatus('중지됨')
    } catch (e) {
      setStatus(`중지 실패: ${String(e)}`)
    }
  }

  const deleteSelected = () => {
    const selNodes = new Set(nodes.filter((n) => n.selected).map((n) => n.id))
    const hasSel = selNodes.size > 0 || edges.some((e) => e.selected)
    if (!hasSel) {
      return
    }
    setEdges((es) => es.filter((e) => !e.selected && !selNodes.has(e.source) && !selNodes.has(e.target)))
    setNodes((ns) => ns.filter((n) => !n.selected))
    if (selectedNodeId && selNodes.has(selectedNodeId)) {
      setSelectedNodeId(null)
    }
  }

  const requestDeleteFlow = () => {
    if (!flowId) {
      return
    }
    setConfirm({
      message: `'${name}' 플로우를 삭제합니다. 되돌릴 수 없습니다.`,
      confirmLabel: '삭제',
      danger: true,
      action: async () => {
        try {
          await api.deleteFlow(flowId)
          setFlowId(null)
          setNodes([])
          setEdges([])
          setDeployedRuns({})
          refreshFlows()
          setStatus('플로우 삭제됨')
        } catch (e) {
          setStatus(`삭제 실패: ${String(e)}`)
        }
      },
    })
  }

  const loadFlow = async (id: string) => {
    try {
      const flow = await api.getFlow(id)
      setFlowId(flow.id)
      setName(flow.name)
      setDeployedRuns({})
      setSelectedNodeId(null)
      setNodes(
        flow.graph.nodes.map((n, i) => {
          const nm = refLabel(n.kind, n.refId, scripts, modules)
          return {
            id: n.id,
            position: { x: 80 + i * 180, y: 120 },
            data: {
              label: nm,
              name: nm,
              kind: n.kind,
              refId: n.refId,
              tickPeriodMs: n.tickPeriodMs ?? 1000,
              params: n.params ?? {},
            },
          }
        }),
      )
      setEdges(
        flow.graph.edges.map((e, i) => ({
          id: `e${i}`,
          source: e.fromNode,
          target: e.toNode,
          label: `${e.fromPort}→${e.toPort}`,
        })),
      )
    } catch (e) {
      setStatus(`불러오기 실패: ${String(e)}`)
    }
  }

  const selectedNode = nodes.find((n) => n.id === selectedNodeId) ?? null

  const updateNodeData = (id: string, patch: Partial<NodeData>) => {
    setNodes((ns) => ns.map((n) => (n.id === id ? { ...n, data: { ...n.data, ...patch } } : n)))
  }

  // 노드를 선택하면 그 노드 설정으로 입력 버퍼를 채운다.
  useEffect(() => {
    const n = nodes.find((x) => x.id === selectedNodeId)
    if (n) {
      setPeriodDraft(String(n.data.tickPeriodMs))
      setParamsDraft(paramsToText(n.data.params))
    }
    // 선택이 바뀔 때만 동기화(타이핑 중 재동기화 방지)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedNodeId])

  return (
    <div className="flow-canvas">
      <div className="toolbar">
        <input className="name-input" value={name} onChange={(e) => setName(e.target.value)} />
        <select value={pick} onChange={(e) => setPick(e.target.value)}>
          <option value="">노드 선택…</option>
          <optgroup label="스크립트">
            {scripts.map((s) => (
              <option key={s.id} value={`script:${s.id}`}>
                {s.name}
              </option>
            ))}
          </optgroup>
          {modules.length > 0 && (
            <optgroup label="모듈">
              {modules.map((m) => (
                <option key={m.id} value={`module:${m.id}`}>
                  {m.name}@{m.version}
                </option>
              ))}
            </optgroup>
          )}
        </select>
        <button onClick={addNode} disabled={!pick}>
          + 노드
        </button>
        <button onClick={deleteSelected} title="선택한 노드/엣지 삭제 (Del)">
          − 선택삭제
        </button>
        <button onClick={save}>저장</button>
        <button className="primary" onClick={deploy}>
          ▶ 배포
        </button>
        <button onClick={stop} disabled={!flowId}>
          ■ 중지
        </button>
        <select value={flowId ?? ''} onChange={(e) => e.target.value && loadFlow(e.target.value)}>
          <option value="">플로우 열기…</option>
          {flows.map((f) => (
            <option key={f.id} value={f.id}>
              {f.name}
            </option>
          ))}
        </select>
        <button className="danger" onClick={requestDeleteFlow} disabled={!flowId}>
          플로우 삭제
        </button>
        <span className="muted small">{status}</span>
      </div>
      <div className="canvas-host">
        <ReactFlow
          nodes={displayNodes}
          edges={edges}
          onNodesChange={onNodesChange}
          onEdgesChange={onEdgesChange}
          onConnect={onConnect}
          onNodeClick={(_, node) => setSelectedNodeId(node.id)}
          onPaneClick={() => setSelectedNodeId(null)}
          deleteKeyCode={['Backspace', 'Delete']}
          fitView
        >
          <Background />
          <Controls />
        </ReactFlow>
        {selectedNode && (
          <div className="flow-config-panel">
            <div className="fcp-head">
              <strong>{selectedNode.data.name}</strong>
              <button className="mini" aria-label="닫기" onClick={() => setSelectedNodeId(null)}>
                ✕
              </button>
            </div>
            <label>
              주기(ms)
              <input
                aria-label="노드 주기(ms)"
                inputMode="numeric"
                value={periodDraft}
                onChange={(e) => {
                  setPeriodDraft(e.target.value)
                  const v = Number(e.target.value)
                  if (Number.isFinite(v) && v > 0) {
                    updateNodeData(selectedNode.id, { tickPeriodMs: v })
                  }
                }}
              />
            </label>
            <label>
              파라미터 (key=value)
              <textarea
                aria-label="노드 파라미터"
                rows={4}
                value={paramsDraft}
                onChange={(e) => {
                  setParamsDraft(e.target.value)
                  updateNodeData(selectedNode.id, { params: parseParams(e.target.value) })
                }}
              />
            </label>
          </div>
        )}
      </div>
      <ConfirmDialog spec={confirm} onClose={() => setConfirm(null)} />
    </div>
  )
}
