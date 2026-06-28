import { BACKEND_BASE } from './client'

const WS_BASE = BACKEND_BASE.replace(/^http/, 'ws')

/**
 * 실행별 로그/메트릭 WebSocket 구독. 반환 함수로 해제.
 */
export function subscribe<T>(
  runId: string,
  channel: 'logs' | 'metrics',
  onMessage: (msg: T) => void,
): () => void {
  let closed = false
  const ws = new WebSocket(`${WS_BASE}/ws/runs/${runId}/${channel}`)
  ws.onmessage = (ev: MessageEvent<string>) => {
    try {
      onMessage(JSON.parse(ev.data) as T)
    } catch {
      // 잘못된 프레임 무시
    }
  }
  return () => {
    if (!closed) {
      closed = true
      ws.close()
    }
  }
}
