import { cleanup, fireEvent, render, screen } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { SelectSheet, type SheetOption } from './SelectSheet'

afterEach(cleanup)

// 検索画面（SearchPage）で、この選択画面を使っている2つの絞り込み
const genres: SheetOption[] = [
  { value: '1', label: 'きれいめ' },
  { value: '2', label: 'カジュアル' },
]
const ageGroups: SheetOption[] = [
  { value: 'TEENS', label: '10代' },
  { value: 'EARLY_20S', label: '20代前半' },
]

const sheets = [
  { title: 'ジャンル', options: genres },
  { title: '年代', options: ageGroups },
]

function renderSheet(title: string, options: SheetOption[], selected: string | null, onApply = vi.fn()) {
  render(<SelectSheet title={title} options={options} selected={selected} onApply={onApply} onClose={() => {}} />)
  return onApply
}

function applyButton() {
  return screen.getByText(/^決定/, { selector: 'button' })
}

describe.each(sheets)('絞り込みの選択画面（$title）', ({ title, options }) => {
  it('何も選んでいないとき、決定ボタンの文字は「決定」だけで、押せる', () => {
    renderSheet(title, options, null)

    expect(applyButton().textContent).toBe('決定')
    expect((applyButton() as HTMLButtonElement).disabled).toBe(false)
  })

  it('選んだ状態で開いても、決定ボタンに選択数を出さない（文字は「決定」だけ）', () => {
    renderSheet(title, options, options[0].value)

    expect(applyButton().textContent).toBe('決定')
    expect((applyButton() as HTMLButtonElement).disabled).toBe(false)
  })

  it('項目を押して選んでも、決定ボタンの文字は「決定」だけのまま', () => {
    renderSheet(title, options, null)

    fireEvent.click(screen.getByRole('button', { name: options[1].label }))

    expect(applyButton().textContent).toBe('決定')
    expect(screen.queryByText(/件/)).toBeNull()
  })

  it('選んだ項目は、選択されていることが分かる（押された状態・チェックの印）', () => {
    renderSheet(title, options, null)

    const chosen = screen.getByRole('button', { name: options[1].label })
    fireEvent.click(chosen)

    expect(chosen.getAttribute('aria-pressed')).toBe('true')
    expect(chosen.classList.contains('search-chip-option-selected')).toBe(true)
    expect(chosen.querySelector('.search-chip-check')?.textContent).toBe('✓')
    const other = screen.getByRole('button', { name: options[0].label })
    expect(other.getAttribute('aria-pressed')).toBe('false')
  })

  it('決定を押すと、選んだ値を渡す（未選択なら null）', () => {
    const onApply = renderSheet(title, options, null)
    fireEvent.click(applyButton())
    expect(onApply).toHaveBeenLastCalledWith(null)

    fireEvent.click(screen.getByRole('button', { name: options[1].label }))
    fireEvent.click(applyButton())
    expect(onApply).toHaveBeenLastCalledWith(options[1].value)
  })
})
