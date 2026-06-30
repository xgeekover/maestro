import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { RunPanel } from './RunPanel'
import type { RunDto } from '../types'

function run(partial: Partial<RunDto>): RunDto {
  return {
    runId: 'x',
    scriptId: 's',
    scriptName: 'X',
    status: 'RUNNING',
    pid: 1,
    restartCount: 0,
    startedAt: null,
    lastError: null,
    ...partial,
  }
}

describe('RunPanel', () => {
  afterEach(() => cleanup())

  it('renders runs and shows 중지 only for live runs', () => {
    const onStop = vi.fn()
    render(
      <RunPanel
        runs={[
          run({ runId: 'a', scriptName: 'Alpha', status: 'RUNNING' }),
          run({ runId: 'b', scriptName: 'Beta', status: 'STOPPED' }),
        ]}
        selectedRunId={null}
        onSelect={vi.fn()}
        onStop={onStop}
      />,
    )
    screen.getByText('Alpha')
    screen.getByText('Beta')
    const stops = screen.getAllByText('중지')
    expect(stops).toHaveLength(1) // RUNNING만 중지 버튼
    fireEvent.click(stops[0])
    expect(onStop).toHaveBeenCalledWith('a')
  })

  it('shows an empty state', () => {
    render(<RunPanel runs={[]} selectedRunId={null} onSelect={vi.fn()} onStop={vi.fn()} />)
    screen.getByText('실행 중인 스크립트가 없습니다.')
  })

  it('calls onSelect when a run is clicked', () => {
    const onSelect = vi.fn()
    render(
      <RunPanel
        runs={[run({ runId: 'a', scriptName: 'Alpha' })]}
        selectedRunId={null}
        onSelect={onSelect}
        onStop={vi.fn()}
      />,
    )
    fireEvent.click(screen.getByText('Alpha'))
    expect(onSelect).toHaveBeenCalledWith('a')
  })
})
