import { describe, expect, it } from 'vitest'
import {
  buildGraph,
  mapNodeStatuses,
  nodePorts,
  paramsToText,
  parseSpecPorts,
  refLabel,
  type CanvasNode,
} from './flowGraph'
import type { RunDto } from '../types'

function node(id: string, over: Partial<CanvasNode['data']> = {}): CanvasNode {
  return {
    id,
    data: { kind: 'SCRIPT', refId: `s-${id}`, tickPeriodMs: 1000, params: {}, ...over },
  }
}

function run(runId: string, status: string): RunDto {
  return {
    runId,
    scriptId: 's',
    scriptName: 'X',
    status,
    pid: 1,
    restartCount: 0,
    startedAt: null,
    lastError: null,
  }
}

describe('buildGraph', () => {
  it('carries per-node config (kind/refId/params/period) into the graph', () => {
    const nodes = [node('n1', { tickPeriodMs: 250, params: { symbol: 'AAPL' } })]
    const g = buildGraph(nodes, [])
    expect(g.nodes).toEqual([
      { id: 'n1', kind: 'SCRIPT', refId: 's-n1', params: { symbol: 'AAPL' }, tickPeriodMs: 250 },
    ])
  })

  it('maps edges with default and custom ports', () => {
    const g = buildGraph(
      [node('a'), node('b')],
      [
        { source: 'a', target: 'b' },
        { source: 'a', target: 'b', sourceHandle: 'left', targetHandle: 'in2' },
      ],
    )
    expect(g.edges).toEqual([
      { fromNode: 'a', fromPort: 'out', toNode: 'b', toPort: 'in' },
      { fromNode: 'a', fromPort: 'left', toNode: 'b', toPort: 'in2' },
    ])
  })
})

describe('mapNodeStatuses', () => {
  it('maps node ids to their run status, skipping unknown runs', () => {
    const statuses = mapNodeStatuses(
      { n1: 'r1', n2: 'r2', n3: 'gone' },
      [run('r1', 'RUNNING'), run('r2', 'ERROR')],
    )
    expect(statuses).toEqual({ n1: 'RUNNING', n2: 'ERROR' })
  })

  it('returns empty when nothing is deployed', () => {
    expect(mapNodeStatuses({}, [run('r1', 'RUNNING')])).toEqual({})
  })
})

describe('paramsToText', () => {
  it('serializes params to key=value lines', () => {
    expect(paramsToText({ a: '1', b: '2' })).toBe('a=1\nb=2')
    expect(paramsToText({})).toBe('')
  })
})

describe('refLabel', () => {
  const scripts = [{ id: 's1', name: 'Ingest' }]
  const modules = [{ id: 'm1', name: 'Filter', version: '2.0.0' }]

  it('resolves a script node to its name', () => {
    expect(refLabel('SCRIPT', 's1', scripts, modules)).toBe('Ingest')
  })

  it('resolves a module node to name@version', () => {
    expect(refLabel('MODULE', 'm1', scripts, modules)).toBe('Filter@2.0.0')
  })

  it('falls back to the refId when not found', () => {
    expect(refLabel('SCRIPT', 'gone', scripts, modules)).toBe('gone')
    expect(refLabel('MODULE', 'gone', scripts, modules)).toBe('gone')
  })
})

describe('parseSpecPorts', () => {
  it('parses declared in/out ports', () => {
    expect(parseSpecPorts('{"in":["a"],"out":["x","y"]}')).toEqual({ in: ['a'], out: ['x', 'y'] })
  })

  it('defaults to single in/out for empty, missing keys, or invalid JSON', () => {
    const def = { in: ['in'], out: ['out'] }
    expect(parseSpecPorts('')).toEqual(def)
    expect(parseSpecPorts('{}')).toEqual(def)
    expect(parseSpecPorts('{bad')).toEqual(def)
    expect(parseSpecPorts('{"in":[]}')).toEqual(def)
  })

  it('ignores non-string port entries', () => {
    expect(parseSpecPorts('{"out":["ok",5,null]}')).toEqual({ in: ['in'], out: ['ok'] })
  })
})

describe('nodePorts', () => {
  const modules = [{ id: 'm1', specJson: '{"in":["a"],"out":["x","y"]}' }]

  it('resolves module ports from specJson', () => {
    expect(nodePorts('MODULE', 'm1', modules)).toEqual({ in: ['a'], out: ['x', 'y'] })
  })

  it('uses default single ports for scripts', () => {
    expect(nodePorts('SCRIPT', 's1', modules)).toEqual({ in: ['in'], out: ['out'] })
  })

  it('uses default ports for an unknown module', () => {
    expect(nodePorts('MODULE', 'gone', modules)).toEqual({ in: ['in'], out: ['out'] })
  })
})
