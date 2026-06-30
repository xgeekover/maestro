import { afterEach, describe, expect, it, vi } from 'vitest'
import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { ToastHost, type Toast } from './ToastHost'

describe('ToastHost', () => {
  afterEach(() => cleanup())

  it('renders nothing when there are no toasts', () => {
    const { container } = render(<ToastHost toasts={[]} onDismiss={vi.fn()} />)
    expect(container.firstChild).toBeNull()
  })

  it('renders each toast message with its kind class', () => {
    const toasts: Toast[] = [
      { id: 1, kind: 'success', message: '저장됨' },
      { id: 2, kind: 'error', message: '실패함' },
    ]
    const { container } = render(<ToastHost toasts={toasts} onDismiss={vi.fn()} />)
    screen.getByText('저장됨')
    screen.getByText('실패함')
    expect(container.querySelector('.toast-success')).not.toBeNull()
    expect(container.querySelector('.toast-error')).not.toBeNull()
  })

  it('calls onDismiss with the toast id when closed', () => {
    const onDismiss = vi.fn()
    render(<ToastHost toasts={[{ id: 7, kind: 'info', message: 'hi' }]} onDismiss={onDismiss} />)
    fireEvent.click(screen.getByLabelText('닫기'))
    expect(onDismiss).toHaveBeenCalledWith(7)
  })
})
