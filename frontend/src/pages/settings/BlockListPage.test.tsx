import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { BlockListResponse } from '../../api/blocks'
import BlockListPage from './BlockListPage'
import SettingsPage from './SettingsPage'

/*
 * ブロック中のアカウント（/settings/blocks。docs/profile.md「ブロック」）の確認
 */

const fetchBlocks = vi.fn<(page: number) => Promise<BlockListResponse>>()
const unblockUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/blocks', () => ({
  fetchBlocks: (page: number) => fetchBlocks(page),
  unblockUser: (id: number) => unblockUser(id),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token', logout: vi.fn() }) }))

const TWO_BLOCKS: BlockListResponse = {
  items: [
    { userId: 3, username: 'ren_027', displayName: 'れん', profileImageUrl: null, blockedAt: '2026-10-12T10:00:00' },
    { userId: 2, username: 'haru_st', displayName: 'はる', profileImageUrl: null, blockedAt: '2026-10-01T09:00:00' },
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
  fetchBlocks.mockReset()
  unblockUser.mockReset()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <Routes>
        <Route path="/settings" element={<SettingsPage />} />
        <Route path="/settings/blocks" element={<BlockListPage />} />
      </Routes>
    </MemoryRouter>,
  )
}

describe('詳細設定', () => {
  it('「ブロック中のアカウント」から一覧を開ける', async () => {
    fetchBlocks.mockResolvedValue(TWO_BLOCKS)
    renderAt('/settings')

    fireEvent.click(screen.getByRole('button', { name: /ブロック中のアカウント/ }))

    expect(await screen.findByRole('heading', { name: 'ブロック中のアカウント' })).toBeTruthy()
  })
})

describe('ブロック中のアカウント', () => {
  it('ブロックした新しい順に、ユーザー名とブロックした日を出す', async () => {
    fetchBlocks.mockResolvedValue(TWO_BLOCKS)
    const { container } = renderAt('/settings/blocks')

    await screen.findByText('@ren_027')
    const rows = container.querySelectorAll('.blocks-item')
    expect(rows).toHaveLength(2)
    expect(rows[0].textContent).toContain('@ren_027')
    expect(rows[0].textContent).toContain('2026/10/12')
    expect(rows[1].textContent).toContain('@haru_st')
  })

  it('1人もいなければ、そのことを出す', async () => {
    fetchBlocks.mockResolvedValue({ items: [], page: 0, size: 20, hasNext: false })
    renderAt('/settings/blocks')

    expect(await screen.findByText('ブロック中のアカウントはありません')).toBeTruthy()
  })

  it('「解除」で確かめてから解除し、一覧から外す', async () => {
    fetchBlocks.mockResolvedValue(TWO_BLOCKS)
    unblockUser.mockResolvedValue(undefined)
    const { container } = renderAt('/settings/blocks')
    await screen.findByText('@haru_st')

    const row = container.querySelectorAll('.blocks-item')[1] as HTMLElement
    fireEvent.click(within(row).getByRole('button', { name: '解除' }))
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('@haru_st さんのブロックを解除しますか？')
    expect(unblockUser).not.toHaveBeenCalled()
    fireEvent.click(within(dialog).getByRole('button', { name: '解除する' }))

    await waitFor(() => expect(screen.queryByText('@haru_st')).toBeNull())
    expect(unblockUser).toHaveBeenCalledWith(2)
    expect(screen.getByText('@ren_027')).toBeTruthy()
  })

  it('確認で「キャンセル」なら解除しない', async () => {
    fetchBlocks.mockResolvedValue(TWO_BLOCKS)
    renderAt('/settings/blocks')
    await screen.findByText('@haru_st')

    fireEvent.click(screen.getAllByRole('button', { name: '解除' })[0])
    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(unblockUser).not.toHaveBeenCalled()
  })
})
