import { BACKEND_BASE } from './client'

const WS_BASE = BACKEND_BASE.replace(/^http/, 'ws')

export type WsState = 'open' | 'reconnecting'

/**
 * 실행별 로그/메트릭 WebSocket 구독. 끊기면 지수 백오프로 자동 재연결한다.
 * 반환 함수로 해제(이후 재연결 중단).
 * onState로 연결 상태 변화를 받을 수 있다(선택).
 */
export function subscribe<T>(
  runId: string,
  channel: 'logs' | 'metrics',
  onMessage: (msg: T) => void,
  onState?: (state: WsState) => void,
): () => void {
  const url = `${WS_BASE}/ws/runs/${runId}/${channel}`
  let disposed = false
  let ws: WebSocket | null = null
  let retry = 0
  let timer: ReturnType<typeof setTimeout> | undefined

  const connect = () => {
    if (disposed) {
      return
    }
    const sock = new WebSocket(url)
    ws = sock
    sock.onopen = () => {
      retry = 0
      onState?.('open')
    }
    sock.onmessage = (ev: MessageEvent<string>) => {
      try {
        onMessage(JSON.parse(ev.data) as T)
      } catch {
        // 잘못된 프레임 무시
      }
    }
    sock.onclose = () => {
      if (disposed) {
        return
      }
      onState?.('reconnecting')
      const delay = Math.min(8000, 500 * 2 ** retry)
      retry += 1
      timer = setTimeout(connect, delay)
    }
  }

  connect()

  return () => {
    disposed = true
    if (timer) {
      clearTimeout(timer)
    }
    ws?.close()
  }
}
