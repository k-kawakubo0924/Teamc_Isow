import { cleanup, fireEvent, render, screen, within } from '@testing-library/react'
import { MemoryRouter, Route, Routes } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type { MastersResponse } from '../../api/masters'
import type { ProfileResponse } from '../../api/profile'
import { MyProfilePage, UserProfilePage } from './ProfilePage'

/*
 * 相手のプロフィールの⋮からのブロックと、ブロック中の相手のプロフィール（docs/profile.md「ブロック」）の確認
 */

const fetchProfile = vi.fn<(id: number) => Promise<ProfileResponse>>()
const fetchMyProfile = vi.fn<() => Promise<ProfileResponse>>()
vi.mock('../../api/profile', () => ({
  fetchProfile: (id: number) => fetchProfile(id),
  fetchMyProfile: () => fetchMyProfile(),
  fetchUserPosts: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  fetchFollowingPosts: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  fetchMyFavorites: async () => ({ posts: [], page: 0, size: 20, hasNext: false }),
  newShuffleSeed: () => 1,
}))
const blockUser = vi.fn<(id: number) => Promise<void>>()
const unblockUser = vi.fn<(id: number) => Promise<void>>()
vi.mock('../../api/blocks', () => ({
  blockUser: (id: number) => blockUser(id),
  unblockUser: (id: number) => unblockUser(id),
}))
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
  fetchMyProfile.mockReset()
  blockUser.mockReset()
  unblockUser.mockReset()
})

function renderOther() {
  return render(
    <MemoryRouter initialEntries={['/users/2']}>
      <Routes>
        <Route path="/users/:userId" element={<UserProfilePage />} />
      </Routes>
    </MemoryRouter>,
  )
}

function openBlockConfirm() {
  fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
  fireEvent.click(screen.getByRole('menuitem', { name: 'ブロック' }))
}

describe('相手のプロフィールの⋮', () => {
  it('⋮のメニューには「通報」と「ブロック」を置く（非表示は作っていない）', async () => {
    fetchProfile.mockResolvedValue(profile())
    renderOther()
    await screen.findByText('@haru_st')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))

    expect(screen.getAllByRole('menuitem').map((item) => item.textContent)).toEqual(['通報', 'ブロック'])
  })

  it('「通報」で、このユーザーを対象にした通報の入力を出す', async () => {
    fetchProfile.mockResolvedValue(profile())
    renderOther()
    await screen.findByText('@haru_st')

    fireEvent.click(screen.getByRole('button', { name: 'メニュー' }))
    fireEvent.click(screen.getByRole('menuitem', { name: '通報' }))

    const dialog = screen.getByRole('dialog', { name: 'ユーザーを通報' })
    expect(within(dialog).getByLabelText('なりすまし')).toBeTruthy()
  })

  it('「ブロック」を押すと画面の中で確かめ、何が起きるか・相手に通知されないことを書く。まだブロックしない', async () => {
    fetchProfile.mockResolvedValue(profile())
    renderOther()
    await screen.findByText('@haru_st')

    openBlockConfirm()

    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('@haru_st さんをブロックしますか？')
    expect(dialog.textContent).toContain('お互いの投稿・プロフィールが見えなくなり')
    expect(dialog.textContent).toContain('フォローは解除され')
    expect(dialog.textContent).toContain('進行中の相談は終了します')
    expect(dialog.textContent).toContain('相手には通知されません')
    expect(document.activeElement).toBe(within(dialog).getByRole('button', { name: 'キャンセル' }))
    expect(blockUser).not.toHaveBeenCalled()
  })

  it('「キャンセル」で閉じ、ブロックしない', async () => {
    fetchProfile.mockResolvedValue(profile())
    renderOther()
    await screen.findByText('@haru_st')
    openBlockConfirm()

    fireEvent.click(screen.getByRole('button', { name: 'キャンセル' }))

    expect(screen.queryByRole('alertdialog')).toBeNull()
    expect(blockUser).not.toHaveBeenCalled()
  })

  it('「ブロックする」でブロックし、その場で「ブロック中のユーザーです」の表示に変わる', async () => {
    fetchProfile.mockResolvedValue(profile())
    blockUser.mockResolvedValue(undefined)
    renderOther()
    await screen.findByText('@haru_st')
    openBlockConfirm()

    fireEvent.click(screen.getByRole('button', { name: 'ブロックする' }))

    expect(await screen.findByText('ブロック中のユーザーです')).toBeTruthy()
    expect(blockUser).toHaveBeenCalledWith(2)
    expect(screen.queryByRole('button', { name: /フォロー/ })).toBeNull()
    expect(screen.getByRole('button', { name: 'ブロックを解除' })).toBeTruthy()
  })

  it('ブロックに失敗したら、確認の中に理由を出す', async () => {
    fetchProfile.mockResolvedValue(profile())
    blockUser.mockRejectedValue(new Error('ブロックできませんでした。'))
    renderOther()
    await screen.findByText('@haru_st')
    openBlockConfirm()

    fireEvent.click(screen.getByRole('button', { name: 'ブロックする' }))

    expect(await within(screen.getByRole('alertdialog')).findByText('ブロックできませんでした。')).toBeTruthy()
  })

  it('自分のプロフィールの⋮は今までどおり詳細設定へのリンクで、ブロックのメニューは出さない', async () => {
    fetchMyProfile.mockResolvedValue(profile({ id: 1, username: 'me', me: true, followingByMe: null }))
    render(
      <MemoryRouter initialEntries={['/profile']}>
        <Routes>
          <Route path="/profile" element={<MyProfilePage />} />
        </Routes>
      </MemoryRouter>,
    )
    await screen.findByText('@me')

    expect(screen.getByRole('link', { name: '詳細設定' }).getAttribute('href')).toBe('/settings')
    expect(screen.queryByRole('button', { name: 'メニュー' })).toBeNull()
  })
})

describe('ブロック中の相手のプロフィール', () => {
  it('投稿・フォロー・相談のボタンを出さず、「ブロック中のユーザーです」と解除のボタンを出す', async () => {
    fetchProfile.mockResolvedValue(profile({ blockedByMe: true }))
    const { container } = renderOther()

    expect(await screen.findByText('ブロック中のユーザーです')).toBeTruthy()
    expect(screen.getByText('@haru_st')).toBeTruthy()
    expect(screen.getByRole('button', { name: 'ブロックを解除' })).toBeTruthy()
    expect(screen.queryByRole('button', { name: /フォロー/ })).toBeNull()
    expect(screen.queryByRole('button', { name: /相談/ })).toBeNull()
    expect(screen.queryByRole('tab')).toBeNull()
    expect(container.textContent).not.toContain('フォロワー')
  })

  it('「ブロックを解除」で確かめてから解除し、プロフィールを読み直す', async () => {
    fetchProfile.mockResolvedValueOnce(profile({ blockedByMe: true })).mockResolvedValueOnce(profile())
    unblockUser.mockResolvedValue(undefined)
    renderOther()
    await screen.findByText('ブロック中のユーザーです')

    fireEvent.click(screen.getByRole('button', { name: 'ブロックを解除' }))
    const dialog = screen.getByRole('alertdialog')
    expect(dialog.textContent).toContain('@haru_st さんのブロックを解除しますか？')
    expect(dialog.textContent).toContain('フォロー・いいね・お気に入りは元に戻りません')
    fireEvent.click(within(dialog).getByRole('button', { name: '解除する' }))

    // 読み直した後は、ふつうのプロフィール（右上の⋮がある）に戻る
    expect(await screen.findByRole('button', { name: 'メニュー' })).toBeTruthy()
    expect(screen.queryByText('ブロック中のユーザーです')).toBeNull()
    expect(unblockUser).toHaveBeenCalledWith(2)
    expect(fetchProfile).toHaveBeenCalledTimes(2)
  })

  it('ブロックされている相手のプロフィール（404）は、存在しないユーザーと同じ表示', async () => {
    const { ApiError } = await import('../../api/client')
    fetchProfile.mockRejectedValue(new ApiError(404, { message: 'ユーザーが見つかりません。', errors: {} }, 'ユーザーが見つかりません。'))
    renderOther()

    expect(await screen.findByText('ユーザーが見つかりません。')).toBeTruthy()
    expect(screen.queryByText(/ブロック/)).toBeNull()
  })
})
