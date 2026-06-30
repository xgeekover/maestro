import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { subscribe } from './ws'

class FakeWebSocket {
  static last: FakeWebSocket | null = null
  url: string
  onmessage: ((ev: MessageEvent<string>) => void) | null = null
  closed = false
  constructor(url: string) {
    this.url = url
    FakeWebSocket.last = this
  }
  close() {
    this.closed = true
  }
}

describe('ws subscribe', () => {
  beforeEach(() => {
    FakeWebSocket.last = null
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })
  afterEach(() => vi.unstubAllGlobals())

  it('connects to the run channel and parses messages', () => {
    const received: unknown[] = []
    const unsub = subscribe('run1', 'logs', (m) => received.push(m))
    expect(FakeWebSocket.last?.url).toBe('ws://localhost:8080/ws/runs/run1/logs')

    FakeWebSocket.last!.onmessage!({ data: JSON.stringify({ level: 'INFO', message: 'hi' }) } as MessageEvent<string>)
    expect(received).toEqual([{ level: 'INFO', message: 'hi' }])

    unsub()
    expect(FakeWebSocket.last!.closed).toBe(true)
  })

  it('ignores malformed frames', () => {
    const received: unknown[] = []
    subscribe('r', 'metrics', (m) => received.push(m))
    FakeWebSocket.last!.onmessage!({ data: 'not json{' } as MessageEvent<string>)
    expect(received).toEqual([])
  })
})
