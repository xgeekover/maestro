import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { ModuleView, moduleFormReady, specJsonError } from './ModuleView'
import { api } from '../api/client'
import type { ModuleDto } from '../types'

// Monaco는 jsdom에서 렌더되지 않으므로 textarea로 대체.
vi.mock('@monaco-editor/react', () => ({
  default: ({ value, onChange }: { value: string; onChange: (v: string) => void }) => (
    <textarea aria-label="source" value={value} onChange={(e) => onChange(e.target.value)} />
  ),
}))

function mod(partial: Partial<ModuleDto>): ModuleDto {
  return {
    id: 'm1',
    name: 'Alpha',
    version: '1.0.0',
    specJson: '{}',
    source: 'class A {}',
    createdAt: '',
    ...partial,
  }
}

describe('specJsonError', () => {
  it('accepts empty and valid JSON, rejects malformed', () => {
    expect(specJsonError('')).toBeNull()
    expect(specJsonError('  ')).toBeNull()
    expect(specJsonError('{"in":["in"]}')).toBeNull()
    expect(specJsonError('{bad')).toMatch(/JSON/)
  })
})

describe('moduleFormReady', () => {
  it('requires name, version and source', () => {
    expect(moduleFormReady('n', '1.0.0', 'src')).toBe(true)
    expect(moduleFormReady('', '1.0.0', 'src')).toBe(false)
    expect(moduleFormReady('n', '', 'src')).toBe(false)
    expect(moduleFormReady('n', '1.0.0', '  ')).toBe(false)
  })
})

describe('ModuleView', () => {
  afterEach(() => {
    cleanup()
    vi.restoreAllMocks()
  })

  it('lists existing modules', async () => {
    vi.spyOn(api, 'listModules').mockResolvedValue([mod({ name: 'Alpha', version: '2.1.0' })])
    render(<ModuleView />)
    await screen.findByText('Alpha')
    screen.getByText('@2.1.0')
  })

  it('creates a module with the form fields', async () => {
    vi.spyOn(api, 'listModules').mockResolvedValue([])
    const create = vi.spyOn(api, 'createModule').mockResolvedValue(mod({ id: 'new', name: 'Widget' }))
    const onModulesChanged = vi.fn()
    render(<ModuleView onModulesChanged={onModulesChanged} />)

    fireEvent.change(screen.getByLabelText('모듈 이름'), { target: { value: 'Widget' } })
    fireEvent.click(screen.getByText('생성'))

    await waitFor(() => expect(onModulesChanged).toHaveBeenCalled())
    expect(create).toHaveBeenCalledWith('Widget', '1.0.0', expect.any(String), expect.any(String))
  })

  it('disables create for an empty name or invalid specJson', async () => {
    vi.spyOn(api, 'listModules').mockResolvedValue([])
    render(<ModuleView />)
    // 이름 비어있음 → 비활성
    expect((screen.getByText('생성') as HTMLButtonElement).disabled).toBe(true)

    fireEvent.change(screen.getByLabelText('모듈 이름'), { target: { value: 'W' } })
    expect((screen.getByText('생성') as HTMLButtonElement).disabled).toBe(false)

    // specJson 깨뜨리면 다시 비활성
    fireEvent.change(screen.getByLabelText('specJson'), { target: { value: '{bad' } })
    expect((screen.getByText('생성') as HTMLButtonElement).disabled).toBe(true)
    screen.getByText(/JSON/)
  })

  it('selecting a module switches to edit mode and saves in place', async () => {
    vi.spyOn(api, 'listModules').mockResolvedValue([mod({ id: 'm1', name: 'Alpha', source: 'class A {}' })])
    const update = vi.spyOn(api, 'updateModule').mockResolvedValue(mod({ id: 'm1', name: 'Alpha' }))
    const onModulesChanged = vi.fn()
    render(<ModuleView onModulesChanged={onModulesChanged} />)

    fireEvent.click(await screen.findByText('Alpha'))
    // 편집 모드: 생성 대신 저장/삭제
    const save = screen.getByText('저장') as HTMLButtonElement
    fireEvent.click(save)

    await waitFor(() => expect(onModulesChanged).toHaveBeenCalled())
    expect(update).toHaveBeenCalledWith('m1', 'Alpha', '1.0.0', '{}', 'class A {}')
  })

  it('deletes a selected module after confirmation', async () => {
    vi.spyOn(api, 'listModules').mockResolvedValue([mod({ id: 'm1', name: 'Alpha' })])
    const del = vi.spyOn(api, 'deleteModule').mockResolvedValue(undefined)
    const onModulesChanged = vi.fn()
    render(<ModuleView onModulesChanged={onModulesChanged} />)

    fireEvent.click(await screen.findByText('Alpha'))
    fireEvent.click(screen.getByText('삭제')) // 툴바 삭제 → 확인 모달
    const dialog = screen.getByRole('dialog')
    fireEvent.click(within(dialog).getByText('삭제')) // 확인

    await waitFor(() => expect(del).toHaveBeenCalledWith('m1'))
    expect(onModulesChanged).toHaveBeenCalled()
  })
})
