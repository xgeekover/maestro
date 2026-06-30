import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { HistoryView } from './HistoryView'
import { api } from '../api/client'
import type { RunHistoryDto } from '../types'

function hist(partial: Partial<RunHistoryDto>): RunHistoryDto {
  return {
    runId: 'r',
    scriptId: 's',
    scriptName: 'X',
    status: 'STOPPED',
    pid: 1,
    restartCount: 0,
    startedAt: '2026-06-01T10:00:00Z',
    endedAt: '2026-06-01T10:05:00Z',
    lastError: null,
    flowId: null,
    nodeId: null,
    ...partial,
  }
}

describe('HistoryView', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('loads and renders history rows (page 0, size 50)', async () => {
    const spy = vi
      .spyOn(api, 'runHistory')
      .mockResolvedValue([
        hist({ runId: 'a', scriptName: 'Alpha' }),
        hist({ runId: 'b', scriptName: 'Beta', status: 'ERROR' }),
      ])
    render(<HistoryView />)
    await screen.findByText('Alpha')
    screen.getByText('Beta')
    expect(spy).toHaveBeenCalledWith(0, 50)
  })

  it('expands the error detail when a row with an error is clicked', async () => {
    vi.spyOn(api, 'runHistory').mockResolvedValue([
      hist({ runId: 'g', scriptName: 'Gamma', status: 'ERROR', lastError: 'boom: NPE' }),
    ])
    render(<HistoryView />)
    const row = await screen.findByText('Gamma')
    fireEvent.click(row)
    await screen.findByText('boom: NPE')
  })

  it('paginates to the next page when a full page is returned', async () => {
    const full = Array.from({ length: 50 }, (_, i) => hist({ runId: `r${i}`, scriptName: `S${i}` }))
    const spy = vi.spyOn(api, 'runHistory').mockResolvedValue(full)
    render(<HistoryView />)
    await screen.findByText('S0')
    fireEvent.click(screen.getByText('다음 →'))
    await waitFor(() => expect(spy).toHaveBeenCalledWith(1, 50))
  })

  it('disables "이전" on the first page', async () => {
    vi.spyOn(api, 'runHistory').mockResolvedValue([hist({ scriptName: 'Solo' })])
    render(<HistoryView />)
    await screen.findByText('Solo')
    expect((screen.getByText('← 이전') as HTMLButtonElement).disabled).toBe(true)
    expect((screen.getByText('다음 →') as HTMLButtonElement).disabled).toBe(true) // 1행 < 50 → 다음 없음
  })
})
