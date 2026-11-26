import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MuteListResponse } from '../../api/mutes'
import MuteListPage from './MuteListPage'
import SettingsPage from './SettingsPage'

/*
 * 投稿を非表示にしている人（/settings/mutes。docs/profile.md「ミュート（投稿の非表示）」）の確認。
 * ブロック中のアカウント（/settings/blocks）と同じ形。ただし解除は確認を出さない（何も壊さず、いつでも戻せるため）
 */

const fetchMutes = vi.fn<(page: number) => Promise<MuteListResponse>>()
const unmuteUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/mutes', () => ({
  fetchMutes: (page: number) => fetchMutes(page),
  unmuteUser: (id: number) => unmuteUser(id),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token', logout: vi.fn() }) }))

const TWO_MUTES: MuteListResponse = {
  items: [
    { userId: 3, username: 'ren_027', displayName: 'れん', profileImageUrl: null, mutedAt: '2026-11-20T10:00:00' },
    { userId: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null, mutedAt: '2026-11-01T09:00:00' },
  ],
  page: 0,
  size: 20,
  hasNext: false,
}

beforeEach(() => {
  // 一覧の読み込みは無限スクロールの目印が画面に入ったときに行うため、置いたらすぐ入ったものとして扱う
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      private readonly callback: IntersectionObserverCallback
      constructor(callback: IntersectionObserverCallback) {
        this.callback = callback
      }
      observe() {
        this.callback([{ isIntersecting: true } as IntersectionObserverEntry], this as unknown as IntersectionObserver)
      }
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  fetchMutes.mockReset()
  unmuteUser.mockReset()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/mutes" element={<MuteListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('詳細設定', () => {
  it('「投稿を非表示にしている人」から一覧を開ける', async () => {
    fetchMutes.mockResolvedValue(TWO_MUTES)
    renderAt('/settings')

    fireEvent.click(screen.getByRole('button', { name: /投稿を非表示にしている人/ }))

    expect(await screen.findByRole('heading', { name: '投稿を非表示にしている人' })).toBeTruthy()
  })
})

describe('投稿を非表示にしている人', () => {
  it('ミュートした新しい順に、ユーザー名と非表示にした日を出す', async () => {
    fetchMutes.mockResolvedValue(TWO_MUTES)
    const { container } = renderAt('/settings/mutes')

    await screen.findByText('@ren_027')
    const rows = container.querySelectorAll('.blocks-item')
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('@ren_027')
    expect(rows[0].textContent).toContain('2026/11/20')
    expect(rows[1].textContent).toContain('@haru_st')
    // 名前からプロフィールを開ける
    expect(within(rows[1] as HTMLElement).getByRole('link', { name: '@haru_st' }).getAttribute('href')).toBe('/users/2')
  })

  it('1人もいなければ、そのことを出す', async () => {
    fetchMutes.mockResolvedValue({ items: [], page: 0, size: 20, hasNext: false })
    renderAt('/settings/mutes')

    expect(await screen.findByText('投稿を非表示にしている人はいません')).toBeTruthy()
  })

  it('「非表示を解除」で、確認を出さずにすぐ解除し、一覧から外して知らせる', async () => {
    fetchMutes.mockResolvedValue(TWO_MUTES)
    unmuteUser.mockResolvedValue(undefined)
    const { container } = renderAt('/settings/mutes')
    await screen.findByText('@haru_st')

    const row = container.querySelectorAll('.blocks-item')[1] as HTMLElement
    fireEvent.click(within(row).getByRole('button', { name: '非表示を解除' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    await waitFor(() => expect(screen.queryByText('@haru_st')).toBeNull())
    expect(unmuteUser).toHaveBeenCalledWith(2)
    expect(screen.getByText('@ren_027')).toBeTruthy()
    const notice = screen.getByText(/@haru_st さんの投稿を/, { selector: '[role="status"]' })
    expect(notice.textContent).toContain('出すように')
  })

  it('解除に失敗したら、そのことを出し、一覧から外さない', async () => {
    fetchMutes.mockResolvedValue(TWO_MUTES)
    unmuteUser.mockRejectedValue(new Error('時間をおいて再度お試しください。'))
    renderAt('/settings/mutes')
    await screen.findByText('@haru_st')

    fireEvent.click(screen.getAllByRole('button', { name: '非表示を解除' })[1])

    expect((await screen.findByRole('alert')).textContent).toContain('時間をおいて再度お試しください。')
    expect(screen.getByText('@haru_st')).toBeTruthy()
  })
})
