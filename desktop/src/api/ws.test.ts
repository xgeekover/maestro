import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { subscribe } from './ws'

class FakeWebSocket {
  static instances: FakeWebSocket[] = []
  static get last(): FakeWebSocket | null {
    return FakeWebSocket.instances.at(-1) ?? null
  }
  url: string
  onopen: (() => void) | null = null
  onclose: (() => void) | null = null
  onmessage: ((ev: MessageEvent<string>) => void) | null = null
  closed = false
  constructor(url: string) {
    this.url = url
    FakeWebSocket.instances.push(this)
  }
  close() {
    this.closed = true
  }
}

describe('ws subscribe', () => {
  beforeEach(() => {
    FakeWebSocket.instances = []
    vi.stubGlobal('WebSocket', FakeWebSocket)
  })
  afterEach(() => {
    vi.unstubAllGlobals()
    vi.useRealTimers()
  })

  it('connects to the run channel and parses messages', () => {
    const received: unknown[] = []
    const unsub = subscribe('run1', 'logs', (m) => received.push(m))
    expect(FakeWebSocket.last?.url).toBe('ws://localhost:8080/ws/runs/run1/logs')

    FakeWebSocket.last!.onmessage!({
      data: JSON.stringify({ level: 'INFO', message: 'hi' }),
    } as MessageEvent<string>)
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

  it('reconnects after an unexpected close and reports state', () => {
    vi.useFakeTimers()
    const states: string[] = []
    subscribe('run1', 'logs', () => {}, (s) => states.push(s))
    expect(FakeWebSocket.instances).toHaveLength(1)

    FakeWebSocket.last!.onclose!() // 예기치 않은 종료
    expect(states).toEqual(['reconnecting'])
    vi.advanceTimersByTime(500) // 첫 백오프
    expect(FakeWebSocket.instances).toHaveLength(2)

    FakeWebSocket.last!.onopen!() // 재연결 성공 → retry 리셋
    expect(states).toEqual(['reconnecting', 'open'])
  })

  it('stops reconnecting after dispose', () => {
    vi.useFakeTimers()
    const unsub = subscribe('run1', 'logs', () => {})
    unsub()
    FakeWebSocket.last!.onclose?.() // 해제 후 종료 이벤트
    vi.advanceTimersByTime(10000)
    expect(FakeWebSocket.instances).toHaveLength(1) // 새 소켓 없음
  })
})
