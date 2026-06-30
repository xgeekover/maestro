import { useEffect } from 'react'

export interface ConfirmSpec {
  message: string
  confirmLabel?: string
  danger?: boolean
  action: () => void
}

interface Props {
  spec: ConfirmSpec | null
  onClose: () => void
}

export function ConfirmDialog({ spec, onClose }: Props) {
  useEffect(() => {
    if (!spec) {
      return
    }
    const onKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape') {
        onClose()
      }
    }
    window.addEventListener('keydown', onKey)
    return () => window.removeEventListener('keydown', onKey)
  }, [spec, onClose])

  if (!spec) {
    return null
  }

  const confirm = () => {
    spec.action()
    onClose()
  }

  return (
    <div className="modal-backdrop" onClick={onClose}>
      <div
        className="modal"
        role="dialog"
        aria-modal="true"
        onClick={(e) => e.stopPropagation()}
      >
        <p className="modal-msg">{spec.message}</p>
        <div className="modal-actions">
          <button onClick={onClose}>취소</button>
          <button className={spec.danger ? 'danger-solid' : 'primary'} onClick={confirm} autoFocus>
            {spec.confirmLabel ?? '확인'}
          </button>
        </div>
      </div>
    </div>
  )
}
