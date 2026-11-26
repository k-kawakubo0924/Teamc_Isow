/// <reference types="node" />
import { readFileSync } from 'node:fs'
import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeAll, describe, expect, it, vi } from 'vitest'
import SearchPage from './SearchPage'

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))
vi.mock('../../api/masters', () => ({
  fetchMasters: () =>
    Promise.resolve({
      fashionCategories: [{ id: 1, name: 'きれいめ' }],
      bodyTypes: [],
      personalColors: [],
      tags: [],
      ageGroups: [{ code: 'TEENS', label: '10代' }],
    }),
}))
vi.mock('../../api/search', () => ({
  fetchSearchHistory: () => Promise.resolve([]),
  fetchSuggestions: () => Promise.resolve([]),
  deleteSearchHistory: () => Promise.resolve(),
  deleteAllSearchHistory: () => Promise.resolve(),
}))

/**
 * 画面と同じ CSS を当て、計算された見た目（枠の色・影）で確かめる。
 * クラス名だけを確かめると、後でスタイルを整理して CSS の規則だけが消えたときに気づけないため。
 * - Vitest は CSS の import（?raw も）を空にするため、ファイルから直接読む
 * - jsdom は var(--…) を解決しないため、index.css の :root の値に置き換えてから当てる
 */
function readCss(relativePath: string): string {
  return readFileSync(new URL(relativePath, import.meta.url), 'utf8')
}

beforeAll(() => {
  const variables = new Map(
    [...readCss('../../index.css').matchAll(/(--[\w-]+)\s*:\s*([^;]+);/g)].map((m) => [m[1], m[2].trim()]),
  )
  const css = readCss('./search.css').replace(/var\((--[\w-]+)\)/g, (all, name: string) => variables.get(name) ?? all)
  const style = document.createElement('style')
  style.textContent = css
  document.head.appendChild(style)
})

afterEach(cleanup)

/** 検索画面を開き、選択肢を読み込み終えた（押せるようになった）検索項目のボタンを返す */
async function openFilterButton(url: string, label: string): Promise<HTMLButtonElement> {
  render(
    <MemoryRouter initialEntries={[url]}>
      <SearchPage />
    </MemoryRouter>,
  )
  const button = (await screen.findByText(label)).closest('button') as HTMLButtonElement
  await vi.waitFor(() => expect(button.disabled).toBe(false))
  return button
}

/** 「絞り込み中」を伝える見た目（枠の色と、内側の線） */
function frameLook(button: HTMLElement) {
  const style = getComputedStyle(button)
  return { borderColor: style.borderColor, boxShadow: style.boxShadow }
}

const filters = [
  { label: 'ジャンル別', filteredUrl: '/search?categoryId=1', selectedName: 'きれいめ' },
  { label: '年代別', filteredUrl: '/search?ageGroup=TEENS', selectedName: '10代' },
]

describe.each(filters)('検索画面の「$label」ボタン（docs/search.md）', ({ label, filteredUrl, selectedName }) => {
  it('絞り込み中は、絞り込んでいないときと枠の見た目が変わる（バッジをなくしたため、これが唯一の手がかり）', async () => {
    const plain = frameLook(await openFilterButton('/search', label))
    cleanup()
    const filtered = await openFilterButton(filteredUrl, label)

    expect(filtered.textContent).toContain(selectedName)
    expect(frameLook(filtered)).not.toEqual(plain)
    // 枠の色が「絞り込み中」の色であること（同じ色に揃えられてしまったときにも気づけるよう、変わったことに加えて確かめる）
    expect(frameLook(filtered).borderColor).not.toBe(plain.borderColor)
  })

  it('絞り込み中でも、選択数のバッジ（「1」）は出さない', async () => {
    const button = await openFilterButton(filteredUrl, label)

    expect(button.querySelector('.search-filter-badge')).toBeNull()
    expect(button.querySelector('.search-filter-label')?.textContent).toBe(label)
  })
})
