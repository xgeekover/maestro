import { describe, expect, it } from 'vitest'
import { filterLogs } from './LogsPanel'
import type { LogEntry } from '../types'

function log(level: string, message: string): LogEntry {
  return { epochMs: 0, level, message, thrown: '' }
}

const logs: LogEntry[] = [
  log('DEBUG', 'connecting socket'),
  log('INFO', 'tick 1 OK'),
  log('WARN', 'slow tick'),
  log('ERROR', 'NullPointerException at line 5'),
]

describe('filterLogs', () => {
  it('ALL returns everything', () => {
    expect(filterLogs(logs, 'ALL', '')).toHaveLength(4)
  })

  it('WARN keeps WARN and ERROR', () => {
    expect(filterLogs(logs, 'WARN', '').map((l) => l.level)).toEqual(['WARN', 'ERROR'])
  })

  it('ERROR keeps only ERROR', () => {
    expect(filterLogs(logs, 'ERROR', '').map((l) => l.level)).toEqual(['ERROR'])
  })

  it('INFO drops DEBUG', () => {
    expect(filterLogs(logs, 'INFO', '').map((l) => l.level)).toEqual(['INFO', 'WARN', 'ERROR'])
  })

  it('search matches message case-insensitively', () => {
    expect(filterLogs(logs, 'ALL', 'nullpointer')).toHaveLength(1)
    expect(filterLogs(logs, 'ALL', 'tick').map((l) => l.message)).toEqual(['tick 1 OK', 'slow tick'])
  })

  it('combines level and search', () => {
    expect(filterLogs(logs, 'WARN', 'tick').map((l) => l.level)).toEqual(['WARN'])
  })

  it('unknown level is treated as INFO rank', () => {
    expect(filterLogs([log('TRACE', 'x')], 'INFO', '')).toHaveLength(1)
    expect(filterLogs([log('TRACE', 'x')], 'WARN', '')).toHaveLength(0)
  })
})
