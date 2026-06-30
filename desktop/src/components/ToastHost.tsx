export type ToastKind = 'error' | 'success' | 'info'

export interface Toast {
  id: number
  kind: ToastKind
  message: string
}

const ICON: Record<ToastKind, string> = {
  error: '⚠',
  success: '✓',
  info: 'ℹ',
}

interface Props {
  toasts: Toast[]
  onDismiss: (id: number) => void
}

export function ToastHost({ toasts, onDismiss }: Props) {
  if (toasts.length === 0) {
    return null
  }
  return (
    <div className="toast-host">
      {toasts.map((t) => (
        <div key={t.id} className={`toast toast-${t.kind}`} role="status">
          <span className="toast-icon">{ICON[t.kind]}</span>
          <span className="toast-msg">{t.message}</span>
          <button className="toast-x" aria-label="닫기" onClick={() => onDismiss(t.id)}>
            ✕
          </button>
        </div>
      ))}
    </div>
  )
}
