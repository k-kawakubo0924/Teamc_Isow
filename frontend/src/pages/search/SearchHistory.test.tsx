import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, describe, expect, it, vi } from 'vitest'
import type { SearchHistoryItem } from '../../api/search'
import SearchPage from './SearchPage'

vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))
vi.mock('../../api/masters', () => ({
  fetchMasters: () =>
    Promise.resolve({ fashionCategories: [], bodyTypes: [], personalColors: [], tags: [], ageGroups: [] }),
}))

const fetchSearchHistory = vi.fn<(token: string) => Promise<SearchHistoryItem[]>>()
const deleteSearchHistory = vi.fn<(id: number, token: string) => Promise<void>>()
vi.mock('../../api/search', () => ({
  fetchSearchHistory: (token: string) => fetchSearchHistory(token),
  fetchSuggestions: () => Promise.resolve([]),
  deleteSearchHistory: (id: number, token: string) => deleteSearchHistory(id, token),
  deleteAllSearchHistory: () => Promise.resolve(),
}))

afterEach(() => {
  cleanup()
  vi.clearAllMocks()
})

/** 新しい順に並んだ n 件の履歴（語1 が一番新しい） */
function histories(n: number): SearchHistoryItem[] {
  return Array.from({ length: n }, (_, i) => ({
    id: i + 1,
    keyword: `語${i + 1}`,
    searchedAt: '2026-11-26T10:00:00',
  }))
}

/** 検索項目を選んでいない検索画面を開き、履歴を読み込み終えたら、履歴の欄を返す */
async function openHistory(items: SearchHistoryItem[]): Promise<HTMLElement> {
  fetchSearchHistory.mockResolvedValue(items)
  deleteSearchHistory.mockResolvedValue()
  render(
    <MemoryRouter initialEntries={['/search']}>
      <SearchPage />
    </MemoryRouter>,
  )
  const title = await screen.findByRole('heading', { name: '検索履歴' })
  const section = title.closest('.search-section') as HTMLElement
  await within(section).findByText('語1')
  return section
}

/** 画面に出ている履歴の語（上から順） */
function shownKeywords(section: HTMLElement): string[] {
  return [...section.querySelectorAll('.search-history-keyword')].map((el) => el.textContent ?? '')
}

function toggle(section: HTMLElement, name: 'もっと見る' | '閉じる') {
  return within(section).queryByRole('button', { name })
}

describe('検索履歴の折りたたみ（docs/search.md）', () => {
  it.each([1, 5])('%i件（5件以下）なら全件出し、「もっと見る」「閉じる」は出さない', async (n) => {
    const section = await openHistory(histories(n))

    expect(shownKeywords(section)).toHaveLength(n)
    expect(toggle(section, 'もっと見る')).toBeNull()
    expect(toggle(section, '閉じる')).toBeNull()
  })

  it.each([6, 20])('%i件（6件以上）なら、新しい5件だけ出し、「もっと見る」を出す（件数は書かない）', async (n) => {
    const section = await openHistory(histories(n))

    expect(shownKeywords(section)).toEqual(['語1', '語2', '語3', '語4', '語5'])
    expect(toggle(section, 'もっと見る')?.textContent).toBe('もっと見る')
    expect(toggle(section, '閉じる')).toBeNull()
  })

  it('「もっと見る」を押すと全件（最大20件）出し、ボタンが「閉じる」に変わる', async () => {
    const section = await openHistory(histories(20))

    fireEvent.click(toggle(section, 'もっと見る') as HTMLElement)

    expect(shownKeywords(section)).toHaveLength(20)
    expect(shownKeywords(section)[19]).toBe('語20')
    expect(toggle(section, '閉じる')?.textContent).toBe('閉じる')
    expect(toggle(section, 'もっと見る')).toBeNull()
  })

  it('「閉じる」を押すと5件の表示に戻り、ボタンが「もっと見る」に戻る', async () => {
    const section = await openHistory(histories(8))
    fireEvent.click(toggle(section, 'もっと見る') as HTMLElement)

    fireEvent.click(toggle(section, '閉じる') as HTMLElement)

    expect(shownKeywords(section)).toEqual(['語1', '語2', '語3', '語4', '語5'])
    expect(toggle(section, 'もっと見る')).not.toBeNull()
    expect(toggle(section, '閉じる')).toBeNull()
  })

  it('全件の表示中に消して残りが5件になったら、「閉じる」を消し、5件をそのまま出す', async () => {
    const section = await openHistory(histories(6))
    fireEvent.click(toggle(section, 'もっと見る') as HTMLElement)

    fireEvent.click(within(section).getByRole('button', { name: '「語3」を履歴から削除' }))

    expect(shownKeywords(section)).toEqual(['語1', '語2', '語4', '語5', '語6'])
    expect(toggle(section, '閉じる')).toBeNull()
    expect(toggle(section, 'もっと見る')).toBeNull()
    expect(deleteSearchHistory).toHaveBeenCalledWith(3, 'test-token')
  })

  it('5件の表示のまま消して残りが5件になったら、「もっと見る」を消し、隠れていた1件も出す', async () => {
    const section = await openHistory(histories(6))

    fireEvent.click(within(section).getByRole('button', { name: '「語1」を履歴から削除' }))

    expect(shownKeywords(section)).toEqual(['語2', '語3', '語4', '語5', '語6'])
    expect(toggle(section, 'もっと見る')).toBeNull()
    expect(toggle(section, '閉じる')).toBeNull()
  })

  it('全件の表示中に5件まで減らしてから、また増えても（読み込み直し）、開いたままにはならず5件の表示から始める', async () => {
    const section = await openHistory(histories(6))
    fireEvent.click(toggle(section, 'もっと見る') as HTMLElement)
    fireEvent.click(within(section).getByRole('button', { name: '「語6」を履歴から削除' }))
    expect(toggle(section, '閉じる')).toBeNull()

    // 削除に失敗すると読み込み直す（SearchHistory の handleDelete）。そのとき6件に戻る場面を作る
    fetchSearchHistory.mockResolvedValue(histories(6))
    deleteSearchHistory.mockRejectedValueOnce(new Error('削除できませんでした'))
    fireEvent.click(within(section).getByRole('button', { name: '「語5」を履歴から削除' }))

    // 読み込み直して6件に戻ると「もっと見る」が出る（開いたままなら「閉じる」が出て、6件目も見える）
    await within(section).findByRole('button', { name: 'もっと見る' })
    expect(shownKeywords(section)).toEqual(['語1', '語2', '語3', '語4', '語5'])
    expect(toggle(section, '閉じる')).toBeNull()
  })
})
