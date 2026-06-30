import { describe, expect, it } from 'vitest'
import { pushSeries, seedSeries, type MetricSeries } from './MetricsPanel'
import type { MetricSnapshot } from '../types'

function snap(partial: Partial<MetricSnapshot>): MetricSnapshot {
  return {
    epochMs: 0,
    heapUsedBytes: 1048576, // 1 MB
    heapMaxBytes: 536870912,
    processCpuLoad: 0.5,
    tickCount: 0,
    errorCount: 0,
    uptimeMs: 0,
    lastTickMs: 2,
    ...partial,
  }
}

describe('seedSeries', () => {
  it('maps snapshots to cpu%/heapMB/tickMs series', () => {
    const s = seedSeries([snap({ processCpuLoad: 0.25, heapUsedBytes: 2097152, lastTickMs: 3 })])
    expect(s).toEqual({ cpu: [25], heap: [2], tick: [3] })
  })

  it('keeps only the most recent `max` points', () => {
    const ms = Array.from({ length: 5 }, (_, i) => snap({ lastTickMs: i }))
    expect(seedSeries(ms, 2).tick).toEqual([3, 4])
  })

  it('returns empty series for no snapshots', () => {
    expect(seedSeries([])).toEqual({ cpu: [], heap: [], tick: [] })
  })
})

describe('pushSeries', () => {
  it('appends a snapshot to all three series', () => {
    const prev: MetricSeries = { cpu: [10], heap: [1], tick: [2] }
    const next = pushSeries(prev, snap({ processCpuLoad: 0.2, heapUsedBytes: 3145728, lastTickMs: 5 }))
    expect(next).toEqual({ cpu: [10, 20], heap: [1, 3], tick: [2, 5] })
  })

  it('caps the series length at `max`', () => {
    const prev: MetricSeries = { cpu: [1, 2], heap: [1, 2], tick: [1, 2] }
    const next = pushSeries(prev, snap({ processCpuLoad: 0.03 }), 2)
    expect(next.cpu).toEqual([2, 3])
  })
})
