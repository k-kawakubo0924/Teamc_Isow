import { useEffect, useId, useRef, useState } from 'react'

export type SheetOption = {
  value: string
  label: string
}

type Props = {
  title: string
  options: SheetOption[]
  /** 開いたときに選ばれている値。null は未選択 */
  selected: string | null
  /** 「決定」で呼ぶ。null は未選択（リセットして決定した場合） */
  onApply: (value: string | null) => void
  /** × ・背景・Esc で閉じる（選び直した内容は反映しない） */
  onClose: () => void
}

/**
 * 画面の下から出す選択画面（design/SearchGenreSelectionScreen.png）。ジャンル・年代の選択で使う。
 * 1つだけ選べる（検索 API が条件を1つずつしか受け付けないため）。同じものをもう一度押すと選択を外す。
 * 選んだ内容は「決定」を押すまで反映しない
 */
export function SelectSheet({ title, options, selected, onApply, onClose }: Props) {
  const titleId = useId()
  const sheetRef = useRef<HTMLDivElement>(null)
  const [draft, setDraft] = useState<string | null>(selected)

  // 開いたら選択画面にフォーカスを移し、Esc で閉じられるようにする
  useEffect(() => {
    sheetRef.current?.focus()
    const handleKeyDown = (e: KeyboardEvent) => {
      if (e.key === 'Escape') onClose()
    }
    document.addEventListener('keydown', handleKeyDown)
    return () => document.removeEventListener('keydown', handleKeyDown)
  }, [onClose])

  return (
    <div className="search-sheet-backdrop" onClick={onClose}>
      <div
        ref={sheetRef}
        className="search-sheet"
        role="dialog"
        aria-modal="true"
        aria-labelledby={titleId}
        tabIndex={-1}
        // 選択画面の中を押しても、背景を押したことにしない
        onClick={(e) => e.stopPropagation()}
      >
        <span className="search-sheet-handle" aria-hidden="true" />
        <header className="search-sheet-header">
          <button type="button" className="search-sheet-close" aria-label="閉じる" onClick={onClose}>
            ×
          </button>
          <h2 className="search-sheet-title" id={titleId}>
            {title}
          </h2>
          <button type="button" className="search-sheet-reset" onClick={() => setDraft(null)} disabled={draft === null}>
            リセット
          </button>
        </header>

        <div className="search-sheet-options">
          {options.map((option) => {
            const checked = draft === option.value
            return (
              <button
                key={option.value}
                type="button"
                className={`search-chip-option${checked ? ' search-chip-option-selected' : ''}`}
                aria-pressed={checked}
                onClick={() => setDraft(checked ? null : option.value)}
              >
                {checked && (
                  <span className="search-chip-check" aria-hidden="true">
                    ✓
                  </span>
                )}
                {option.label}
              </button>
            )
          })}
        </div>

        <div className="search-sheet-footer">
          <button type="button" className="search-sheet-apply" onClick={() => onApply(draft)}>
            {draft === null ? '決定' : '決定（1件）'}
          </button>
        </div>
      </div>
    </div>
  )
}
