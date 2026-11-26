import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MastersResponse } from '../../api/masters'
import type { ProfileResponse } from '../../api/profile'
import { UserProfilePage } from './ProfilePage'

/*
 * 相手のプロフィールの⋮からのミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）の確認。
 * - 文言は「非表示」「非表示を解除」。「ミュート」「非表示」を単独の語で使わない
 * - 確認のダイアログは出さない（何も壊さず、いつでも解除できる軽い手段のため）
 * - ミュートしても見ているものは消えないため、実行したことを画面で伝える（メニューの文言が変わり、短い知らせを出す）
 * - ミュートしている相手のプロフィールには、そのことを出す（ミュートしたことを忘れて悩まないように）
 */

const fetchProfile = vi.fn<(id: number) => Promise<ProfileResponse>>()
vi.mock('../../api/profile', () => ({
  fetchProfile: (id: number) => fetchProfile(id),
  fetchMyProfile: vi.fn(),
  fetchUserPosts: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  fetchFollowingPosts: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  fetchMyFavorites: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  newShuffleSeed: () => 1,
}))
const muteUser = vi.fn<(id: number) => Promise<void>>()
const unmuteUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/mutes', () => ({
  muteUser: (id: number) => muteUser(id),
  unmuteUser: (id: number) => unmuteUser(id),
}))
vi.mock('../../api/blocks', () => ({ blockUser: vi.fn(), unblockUser: vi.fn() }))
vi.mock('../../api/masters', () => ({
  fetchMasters: async (): Promise<MastersResponse> => ({
    fashionCategories: [],
    bodyTypes: [],
    personalColors: [],
    tags: [],
    ageGroups: [],
    genders: [],
  }),
}))
vi.mock('../../api/consultations', () => ({
  fetchConsultationStatus: async () => ({ available: true, reason: null, availableAt: null, conversationId: null }),
}))
vi.mock('../../api/follows', () => ({ setFollow: vi.fn() }))
vi.mock('../../api/reports', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/reports')>()),
  reportTarget: vi.fn(),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'test-token' }) }))

function profile(overrides: Partial<ProfileResponse> = {}): ProfileResponse {
  return {
    id: 2,
    username: 'haru_st',
    displayName: 'はる',
    profileImageUrl: null,
    gender: null,
    heightCm: null,
    ageGroup: null,
    bodyType: null,
    personalColor: null,
    postCount: 3,
    followingCount: 4,
    followerCount: 5,
    me: false,
    followingByMe: false,
    blockedByMe: false,
    mutedByMe: false,
    ...overrides,
  }
}

beforeEach(() => {
  vi.stubGlobal(
    'IntersectionObserver',
    class {
      observe() {}
      disconnect() {}
    },
  )
})

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  fetchProfile.mockReset()
  muteUser.mockReset()
  unmuteUser.mockReset()
})

async function openProfile(response: ProfileResponse) {
  fetchProfile.mockResolvedValue(response)
  const result = render(
    <MemoryRouter initialEntries={['/users/2']}>
      <Routes>
        <Route path="/users/:userId" element={<UserProfilePage />} />
        <Route path="/" element={<p>ホーム画面</p>} />
      </Routes>
    </MemoryRouter>,
  )
  await screen.findByText('@haru_st')
  return result
}

function menuItems(): string[] {
  fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
  return screen.getAllByRole('menuitem').map((item) => item.textContent ?? '')
}

/** 実行したことの知らせ（role="status"）。一覧の「読み込み中...」と取り違えないよう、文で探す */
function findNotice() {
  return screen.findByText(/@haru_st さんの投稿を/, { selector: '[role="status"]' })
}

const MUTED_NOTE = 'この人の投稿を非表示にしています'

describe('相手のプロフィールの⋮（ミュート）', () => {
  it('⋮には「通報」「非表示」「ブロック」を並べる（メニューは短く。何をしたかは押した後の知らせとプロフィールの表示で説明する）', async () => {
    await openProfile(profile())

    const items = menuItems()

    expect(items).toEqual(['通報', '非表示', 'ブロック'])
    // 「ミュート」の語は画面に出さない（docs とコードだけで使う）
    expect(items).not.toContain('ミュート')
  })

  it('ミュートしている相手なら、⋮は「非表示を解除」で、プロフィールにミュート中であることを出す', async () => {
    await openProfile(profile({ mutedByMe: true }))

    expect(screen.getByText(MUTED_NOTE)).toBeTruthy()
    expect(menuItems()).toEqual(['通報', '非表示を解除', 'ブロック'])
  })

  it('ミュートしていない相手には、ミュート中の表示を出さない', async () => {
    await openProfile(profile())

    expect(screen.queryByText(MUTED_NOTE)).toBeNull()
  })

  it('「非表示」は確認を出さずにすぐミュートし、その場に留まって、したことを知らせる', async () => {
    muteUser.mockResolvedValue(undefined)
    await openProfile(profile())

    menuItems()
    fireEvent.click(screen.getByRole('menuitem', { name: '非表示' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(screen.queryByRole('dialog')).toBeNull()
    await waitFor(() => expect(muteUser).toHaveBeenCalledWith(2))
    // 何をしたかを短く知らせる（ミュートしても見ているものは消えないため、知らせないと押せたか分からない）
    const notice = await findNotice()
    expect(notice.textContent).toContain('@haru_st')
    expect(notice.textContent).toContain('出さないように')
    expect(screen.getByText(MUTED_NOTE)).toBeTruthy()
    // その場に留まり、プロフィールはそのまま見える
    expect(screen.getByText('@haru_st')).toBeTruthy()
    expect(screen.queryByText('ホーム画面')).toBeNull()
    // メニューの文言がその場で変わる
    expect(menuItems()).toContain('非表示を解除')
  })

  it('「非表示を解除」は確認を出さずにすぐ解除し、ミュート中の表示を消して、したことを知らせる', async () => {
    unmuteUser.mockResolvedValue(undefined)
    await openProfile(profile({ mutedByMe: true }))

    menuItems()
    fireEvent.click(screen.getByRole('menuitem', { name: '非表示を解除' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    await waitFor(() => expect(unmuteUser).toHaveBeenCalledWith(2))
    await waitFor(() => expect(screen.queryByText(MUTED_NOTE)).toBeNull())
    expect((await findNotice()).textContent).toContain('出すように')
    expect(menuItems()).toContain('非表示')
  })

  it('ミュートに失敗したら、そのことを出し、ミュート中の表示にはしない', async () => {
    muteUser.mockRejectedValue(new Error('時間をおいて再度お試しください。'))
    await openProfile(profile())

    menuItems()
    fireEvent.click(screen.getByRole('menuitem', { name: '非表示' }))

    expect((await screen.findByRole('alert')).textContent).toContain('時間をおいて再度お試しください。')
    expect(screen.queryByText(MUTED_NOTE)).toBeNull()
    expect(menuItems()).toContain('非表示')
  })

  it('ミュートしていても、ブロックの確認はこれまでどおり出る（ミュートとブロックは別の操作）', async () => {
    await openProfile(profile({ mutedByMe: true }))

    menuItems()
    fireEvent.click(screen.getByRole('menuitem', { name: 'ブロック' }))

    expect(within(screen.getByRole('alertdialog')).getByText(/@haru_st さんをブロックしますか？/)).toBeTruthy()
  })
})
