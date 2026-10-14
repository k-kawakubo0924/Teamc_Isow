import { useEffect, useId, useRef } from 'react'
import './blockConfirm.css'

/**
 * ブロック・解除の確認（ブラウザの confirm ではなく、画面の中で確かめる）。
 * 開いたら「キャンセル」を選んだ状態にする（誤って Enter で実行しないため）。Esc で閉じる（通信中は閉じない）
 */
export function BlockConfirmDialog({
  title,
  body,
  confirmLabel,
  busyLabel,
  busy,
  error,
  onConfirm,
  onCancel,
}: {
  title: string
  body: string
  confirmLabel: string
  /** 通信中のボタンの文言 */
  busyLabel: string
  busy: boolean
  error: string | null
  onConfirm: () => void
  onCancel: () => void
}) {
  const titleId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)

  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [busy, onCancel])

  return (
    <div className="block-confirm-backdrop">
      <div className="block-confirm" role="alertdialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className="block-confirm-title">
          {title}
        </h2>
        <p className="block-confirm-body">{body}</p>
        {error && (
          <p className="block-confirm-error" role="alert">
            {error}
          </p>
        )}
        <div className="block-confirm-buttons">
          <button type="button" ref={cancelRef} className="block-confirm-cancel" disabled={busy} onClick={onCancel}>
            キャンセル
          </button>
          <button type="button" className="block-confirm-ok" disabled={busy} onClick={onConfirm}>
            {busy ? busyLabel : confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
