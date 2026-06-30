import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { ConfirmDialog, type ConfirmSpec } from './ConfirmDialog'

function spec(partial: Partial<ConfirmSpec> = {}): ConfirmSpec {
  return { message: '정말 진행할까요?', action: vi.fn(), ...partial }
}

describe('ConfirmDialog', () => {
  afterEach(() => cleanup())

  it('renders nothing without a spec', () => {
    const { container } = render(<ConfirmDialog spec={null} onClose={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders the message and confirm label', () => {
    render(<ConfirmDialog spec={spec({ confirmLabel: '삭제' })} onClose={vi.fn()} />)
    screen.getByText('정말 진행할까요?')
    screen.getByText('삭제')
  })

  it('runs the action and closes on confirm', () => {
    const action = vi.fn()
    const onClose = vi.fn()
    render(<ConfirmDialog spec={spec({ action, confirmLabel: '확인' })} onClose={onClose} />)
    fireEvent.click(screen.getByText('확인'))
    expect(action).toHaveBeenCalledTimes(1)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('cancel closes without running the action', () => {
    const action = vi.fn()
    const onClose = vi.fn()
    render(<ConfirmDialog spec={spec({ action })} onClose={onClose} />)
    fireEvent.click(screen.getByText('취소'))
    expect(action).not.toHaveBeenCalled()
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('backdrop click closes; body click does not', () => {
    const onClose = vi.fn()
    const { container } = render(<ConfirmDialog spec={spec()} onClose={onClose} />)
    fireEvent.click(container.querySelector('.modal')!)
    expect(onClose).not.toHaveBeenCalled()
    fireEvent.click(container.querySelector('.modal-backdrop')!)
    expect(onClose).toHaveBeenCalledTimes(1)
  })

  it('uses the solid-danger style when danger is set', () => {
    render(<ConfirmDialog spec={spec({ danger: true, confirmLabel: '삭제' })} onClose={vi.fn()} />)
    expect(screen.getByText('삭제').className).toContain('danger-solid')
  })
})
