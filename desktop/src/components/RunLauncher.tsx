import { useState } from 'react'
import type { CreateRunRequest } from '../types'

export type RunConfig = Omit<CreateRunRequest, 'scriptId'>

interface Props {
  disabled?: boolean
  onRun: (config: RunConfig) => void
}

// "key=value" 줄들을 파싱. 빈 줄/주석(#)/등호 없는 줄은 무시.
export function parseParams(text: string): Record<string, string> {
  const out: Record<string, string> = {}
  for (const line of text.split('\n')) {
    const t = line.trim()
    if (!t || t.startsWith('#')) {
      continue
    }
    const i = t.indexOf('=')
    if (i <= 0) {
      continue
    }
    out[t.slice(0, i).trim()] = t.slice(i + 1).trim()
  }
  return out
}

// 양수만 채택, 그 외(빈칸·0·음수·NaN)는 생략 → 백엔드 기본값/클램프 사용.
function positive(text: string): number | undefined {
  const n = Number(text)
  return Number.isFinite(n) && n > 0 ? n : undefined
}

export function RunLauncher({ disabled, onRun }: Props) {
  const [open, setOpen] = useState(false)
  const [periodMs, setPeriodMs] = useState('1000')
  const [paramsText, setParamsText] = useState('')
  const [stopOnError, setStopOnError] = useState(false)
  const [maxHeapMb, setMaxHeapMb] = useState('')
  const [tickTimeoutMs, setTickTimeoutMs] = useState('')
  const [errorThreshold, setErrorThreshold] = useState('')

  const launch = () => {
    const cfg: RunConfig = {}
    const period = positive(periodMs)
    if (period !== undefined) {
      cfg.tickPeriodMs = period
    }
    const params = parseParams(paramsText)
    if (Object.keys(params).length > 0) {
      cfg.params = params
    }
    if (stopOnError) {
      cfg.stopOnError = true
    }
    const heapMb = positive(maxHeapMb)
    if (heapMb !== undefined) {
      cfg.maxHeapBytes = Math.round(heapMb * 1048576)
    }
    const tt = positive(tickTimeoutMs)
    if (tt !== undefined) {
      cfg.tickTimeoutMs = tt
    }
    const et = positive(errorThreshold)
    if (et !== undefined) {
      cfg.errorThreshold = et
    }
    onRun(cfg)
    setOpen(false)
  }

  return (
    <div className="run-launcher">
      <button className="primary" disabled={disabled} onClick={launch} title="현재 설정으로 실행">
        ▶ 실행
      </button>
      <button
        className="mini"
        disabled={disabled}
        aria-label="실행 설정"
        title="실행 설정"
        onClick={() => setOpen((o) => !o)}
      >
        ⚙
      </button>
      {open && (
        <div className="run-config" role="group" aria-label="실행 설정">
          <label>
            주기(ms)
            <input
              value={periodMs}
              onChange={(e) => setPeriodMs(e.target.value)}
              inputMode="numeric"
            />
          </label>
          <label>
            파라미터 (key=value, 줄바꿈)
            <textarea
              value={paramsText}
              onChange={(e) => setParamsText(e.target.value)}
              placeholder={'symbol=AAPL\nthreshold=10'}
              rows={3}
            />
          </label>
          <details>
            <summary>고급</summary>
            <label className="row">
              <input
                type="checkbox"
                checked={stopOnError}
                onChange={(e) => setStopOnError(e.target.checked)}
              />
              에러 시 중지
            </label>
            <label>
              최대 힙(MB)
              <input
                value={maxHeapMb}
                onChange={(e) => setMaxHeapMb(e.target.value)}
                placeholder="기본값"
                inputMode="numeric"
              />
            </label>
            <label>
              tick 타임아웃(ms)
              <input
                value={tickTimeoutMs}
                onChange={(e) => setTickTimeoutMs(e.target.value)}
                placeholder="기본값"
                inputMode="numeric"
              />
            </label>
            <label>
              에러 임계치
              <input
                value={errorThreshold}
                onChange={(e) => setErrorThreshold(e.target.value)}
                placeholder="기본값"
                inputMode="numeric"
              />
            </label>
          </details>
          <div className="rc-actions">
            <button className="primary" onClick={launch}>
              이 설정으로 실행
            </button>
          </div>
        </div>
      )}
    </div>
  )
}
