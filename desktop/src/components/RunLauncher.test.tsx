import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { RunLauncher, parseParams } from './RunLauncher'

describe('parseParams', () => {
  it('parses key=value lines and ignores blanks/comments/invalid', () => {
    const out = parseParams('symbol=AAPL\n\n# 주석\nthreshold=10\nbad line\n=novalue')
    expect(out).toEqual({ symbol: 'AAPL', threshold: '10' })
  })

  it('keeps "=" inside the value', () => {
    expect(parseParams('url=https://x?a=1')).toEqual({ url: 'https://x?a=1' })
  })
})

describe('RunLauncher', () => {
  afterEach(() => cleanup())

  it('runs with default config (period 1000, no params)', () => {
    const onRun = vi.fn()
    render(<RunLauncher onRun={onRun} />)
    fireEvent.click(screen.getByText('▶ 실행'))
    expect(onRun).toHaveBeenCalledWith({ tickPeriodMs: 1000 })
  })

  it('builds a full config from the settings drawer', () => {
    const onRun = vi.fn()
    render(<RunLauncher onRun={onRun} />)
    fireEvent.click(screen.getByLabelText('실행 설정')) // ⚙ 토글

    fireEvent.change(screen.getByLabelText(/주기/), { target: { value: '250' } })
    fireEvent.change(screen.getByLabelText(/파라미터/), {
      target: { value: 'symbol=AAPL\nthreshold=10' },
    })
    fireEvent.click(screen.getByLabelText('에러 시 중지'))
    fireEvent.change(screen.getByLabelText(/최대 힙/), { target: { value: '256' } })

    fireEvent.click(screen.getByText('이 설정으로 실행'))
    expect(onRun).toHaveBeenCalledWith({
      tickPeriodMs: 250,
      params: { symbol: 'AAPL', threshold: '10' },
      stopOnError: true,
      maxHeapBytes: 256 * 1048576,
    })
  })

  it('omits non-positive numeric fields so backend defaults apply', () => {
    const onRun = vi.fn()
    render(<RunLauncher onRun={onRun} />)
    fireEvent.click(screen.getByLabelText('실행 설정'))
    fireEvent.change(screen.getByLabelText(/주기/), { target: { value: '0' } })
    fireEvent.click(screen.getByText('이 설정으로 실행'))
    expect(onRun).toHaveBeenCalledWith({}) // 주기 0 → 생략
  })

  it('disables controls when disabled', () => {
    render(<RunLauncher disabled onRun={vi.fn()} />)
    expect((screen.getByText('▶ 실행') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByLabelText('실행 설정') as HTMLButtonElement).disabled).toBe(true)
  })
})
