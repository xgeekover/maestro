import { afterEach, describe, expect, it, vi } from 'vitest'
import { api, BACKEND_BASE } from './client'

function mockFetch(status: number, body: string) {
  return vi.spyOn(globalThis, 'fetch').mockResolvedValue(new Response(body, { status }))
}

describe('api client', () => {
  afterEach(() => vi.restoreAllMocks())

  it('listScripts GETs /api/scripts', async () => {
    const f = mockFetch(200, JSON.stringify([{ id: '1', name: 'a' }]))
    const r = await api.listScripts()
    expect(f.mock.calls[0][0]).toBe(`${BACKEND_BASE}/api/scripts`)
    expect(r).toEqual([{ id: '1', name: 'a' }])
  })

  it('createScript POSTs JSON body', async () => {
    const f = mockFetch(201, '{"id":"x"}')
    await api.createScript('n', 'src')
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/scripts`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({ name: 'n', source: 'src' })
  })

  it('startRun POSTs the run request', async () => {
    const f = mockFetch(202, '{"runId":"r"}')
    const r = await api.startRun({ scriptId: 's', tickPeriodMs: 100 })
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/runs`)
    expect(JSON.parse(init.body as string)).toEqual({ scriptId: 's', tickPeriodMs: 100 })
    expect(r).toEqual({ runId: 'r' })
  })

  it('updatePeriod POSTs tickPeriodMs to /period', async () => {
    const f = mockFetch(202, '')
    await api.updatePeriod('r1', 250)
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/runs/r1/period`)
    expect(JSON.parse(init.body as string)).toEqual({ tickPeriodMs: 250 })
  })

  it('deleteScript uses DELETE', async () => {
    const f = mockFetch(200, '')
    await api.deleteScript('x')
    expect((f.mock.calls[0][1] as RequestInit).method).toBe('DELETE')
  })

  it('runHistory builds a paginated query', async () => {
    const f = mockFetch(200, '[]')
    await api.runHistory(2, 25)
    expect(f.mock.calls[0][0]).toBe(`${BACKEND_BASE}/api/runs/history?page=2&size=25`)
  })

  it('createModule POSTs name/version/specJson/source', async () => {
    const f = mockFetch(201, '{"id":"m1"}')
    await api.createModule('mod', '1.2.0', '{"in":["in"]}', 'class M {}')
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/modules`)
    expect(init.method).toBe('POST')
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'mod',
      version: '1.2.0',
      specJson: '{"in":["in"]}',
      source: 'class M {}',
    })
  })

  it('getModule GETs /api/modules/{id}', async () => {
    const f = mockFetch(200, '{"id":"m1"}')
    await api.getModule('m1')
    expect(f.mock.calls[0][0]).toBe(`${BACKEND_BASE}/api/modules/m1`)
  })

  it('updateModule PUTs to /api/modules/{id}', async () => {
    const f = mockFetch(200, '{"id":"m1"}')
    await api.updateModule('m1', 'mod', '1.1.0', '{}', 'class M {}')
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/modules/m1`)
    expect(init.method).toBe('PUT')
    expect(JSON.parse(init.body as string)).toEqual({
      name: 'mod',
      version: '1.1.0',
      specJson: '{}',
      source: 'class M {}',
    })
  })

  it('deleteModule uses DELETE', async () => {
    const f = mockFetch(200, '')
    await api.deleteModule('m1')
    const [url, init] = f.mock.calls[0] as [string, RequestInit]
    expect(url).toBe(`${BACKEND_BASE}/api/modules/m1`)
    expect((init as RequestInit).method).toBe('DELETE')
  })

  it('throws on a non-ok response', async () => {
    mockFetch(404, 'not found')
    await expect(api.getScript('ghost')).rejects.toThrow(/404/)
  })
})
