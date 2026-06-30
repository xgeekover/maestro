import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { RunControlBar } from './RunControlBar'
import type { RunDto } from '../types'

function run(partial: Partial<RunDto>): RunDto {
  return {
    runId: 'r1',
    scriptId: 's',
    scriptName: 'X',
    status: 'RUNNING',
    pid: 42,
    restartCount: 0,
    startedAt: null,
    lastError: null,
    ...partial,
  }
}

describe('RunControlBar', () => {
  afterEach(() => cleanup())

  it('renders nothing without a run', () => {
    const { container } = render(<RunControlBar run={null} onUpdatePeriod={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('shows the period control for a RUNNING run and applies it', () => {
    const onUpdatePeriod = vi.fn()
    render(<RunControlBar run={run({ status: 'RUNNING' })} onUpdatePeriod={onUpdatePeriod} />)
    const input = screen.getByLabelText('새 주기(ms)')
    fireEvent.change(input, { target: { value: '500' } })
    fireEvent.click(screen.getByText('주기 적용'))
    expect(onUpdatePeriod).toHaveBeenCalledWith('r1', 500)
  })

  it('hides the period control for non-RUNNING runs', () => {
    render(<RunControlBar run={run({ status: 'STOPPED' })} onUpdatePeriod={vi.fn()} />)
    expect(screen.queryByLabelText('새 주기(ms)')).toBeNull()
    screen.getByText('STOPPED') // 상태는 여전히 표시
  })

  it('ignores non-positive period input', () => {
    const onUpdatePeriod = vi.fn()
    render(<RunControlBar run={run({ status: 'RUNNING' })} onUpdatePeriod={onUpdatePeriod} />)
    fireEvent.change(screen.getByLabelText('새 주기(ms)'), { target: { value: '-5' } })
    fireEvent.click(screen.getByText('주기 적용'))
    expect(onUpdatePeriod).not.toHaveBeenCalled()
  })
})
