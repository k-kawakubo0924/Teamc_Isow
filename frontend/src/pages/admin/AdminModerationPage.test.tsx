import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import type {
  HidePostRequest,
  ModerationPostState,
  ModerationUserState,
  SuspendUserRequest,
} from '../../api/admin'
import { ApiError } from '../../api/client'
import AdminModerationPage from './AdminModerationPage'

/*
 * 管理画面の措置（docs/admin.md「6. 措置」）の確認。
 * ユーザー名・投稿 ID で探し、理由（と期間）を選び、確認してから措置する。措置中の一覧も出す
 */

const findModerationUser = vi.fn<(username: string) => Promise<ModerationUserState>>()
const fetchModerationUser = vi.fn<(id: number) => Promise<ModerationUserState>>()
const suspendUser = vi.fn<(id: number, request: SuspendUserRequest) => Promise<ModerationUserState>>()
const unsuspendUser = vi.fn<(id: number, memo: string) => Promise<ModerationUserState>>()
const fetchModerationPost = vi.fn<(id: number) => Promise<ModerationPostState>>()
const hidePost = vi.fn<(id: number, request: HidePostRequest) => Promise<ModerationPostState>>()
const unhidePost = vi.fn<(id: number, memo: string) => Promise<ModerationPostState>>()
const listHiddenPosts = vi.fn()
const listSuspendedUsers = vi.fn()
vi.mock('../../api/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('../../api/admin')>()),
  findModerationUser: (username: string) => findModerationUser(username),
  fetchModerationUser: (id: number) => fetchModerationUser(id),
  suspendUser: (id: number, request: SuspendUserRequest) => suspendUser(id, request),
  unsuspendUser: (id: number, memo: string) => unsuspendUser(id, memo),
  fetchModerationPost: (id: number) => fetchModerationPost(id),
  hidePost: (id: number, request: HidePostRequest) => hidePost(id, request),
  unhidePost: (id: number, memo: string) => unhidePost(id, memo),
  listHiddenPosts: () => listHiddenPosts(),
  listSuspendedUsers: () => listSuspendedUsers(),
}))
vi.mock('../../auth/authContext', () => ({ useAuth: () => ({ token: 'token', isLoggedIn: true }) }))

function user(overrides: Partial<ModerationUserState> = {}): ModerationUserState {
  return {
    userId: 9,
    username: 'ren_027',
    admin: false,
    suspended: false,
    suspendedAt: null,
    suspendedUntil: null,
    suspensionReason: null,
    suspensionReasonLabel: null,
    history: [],
    ...overrides,
  }
}

function postState(overrides: Partial<ModerationPostState> = {}): ModerationPostState {
  return {
    postId: 5,
    title: '秋のコーデ',
    authorId: 9,
    authorUsername: 'ren_027',
    createdAt: '2026-10-09T08:00:00',
    hidden: false,
    hiddenAt: null,
    hiddenReason: null,
    hiddenReasonLabel: null,
    history: [],
    ...overrides,
  }
}

const SUSPENDED = user({
  suspended: true,
  suspendedAt: '2026-10-12T10:00:00',
  suspendedUntil: '2026-10-19T10:00:00',
  suspensionReason: 'HARASSMENT',
  suspensionReasonLabel: '嫌がらせ・誹謗中傷',
  history: [
    {
      id: 1,
      action: 'SUSPEND_USER',
      reason: 'HARASSMENT',
      reasonLabel: '嫌がらせ・誹謗中傷',
      suspendedUntil: '2026-10-19T10:00:00',
      reportCaseId: 7,
      adminUsername: 'admin_k',
      memo: '何度も送っている',
      createdAt: '2026-10-12T10:00:00',
    },
  ],
})

beforeEach(() => {
  listHiddenPosts.mockResolvedValue({
    items: [
      {
        postId: 4,
        title: '非公開の投稿',
        authorUsername: 'aoi',
        hiddenAt: '2026-10-11T09:00:00',
        hiddenReason: 'SPAM',
        hiddenReasonLabel: 'スパム・宣伝',
      },
    ],
    page: 0,
    size: 20,
    hasNext: false,
  })
  listSuspendedUsers.mockResolvedValue({
    items: [
      {
        userId: 8,
        username: 'mio',
        suspendedAt: '2026-10-11T09:00:00',
        suspendedUntil: null,
        suspensionReason: 'SPAM',
        suspensionReasonLabel: 'スパム・宣伝',
      },
    ],
    page: 0,
    size: 20,
    hasNext: false,
  })
})

afterEach(() => {
  cleanup()
  vi.resetAllMocks()
})

function renderPage() {
  return render(
    <MemoryRouter>
      <AdminModerationPage />
    </MemoryRouter>,
  )
}

async function searchUser(username: string) {
  fireEvent.change(screen.getByLabelText('ユーザー名'), { target: { value: username } })
  fireEvent.click(screen.getByRole('button', { name: 'ユーザーを探す' }))
  await screen.findByRole('heading', { name: `@${username}` })
}

async function searchPost(id: string) {
  fireEvent.change(screen.getByLabelText('投稿 ID'), { target: { value: id } })
  fireEvent.click(screen.getByRole('button', { name: '投稿を探す' }))
}

describe('ユーザーの利用停止', () => {
  it('理由と期間を選び、確認してから利用停止にする。確認の前には送らない', async () => {
    findModerationUser.mockResolvedValue(user())
    suspendUser.mockResolvedValue(SUSPENDED)
    renderPage()
    await searchUser('ren_027')
    const panel = screen.getByRole('region', { name: '@ren_027' })

    fireEvent.change(within(panel).getByLabelText('理由'), { target: { value: 'HARASSMENT' } })
    fireEvent.change(within(panel).getByLabelText('期間'), { target: { value: '7' } })
    fireEvent.change(within(panel).getByLabelText('メモ（管理者だけが見ます）'), { target: { value: 'メモ' } })
    fireEvent.click(within(panel).getByRole('button', { name: '利用停止にする' }))

    const dialog = await screen.findByRole('dialog')
    expect(dialog.textContent).toContain('@ren_027 を利用停止にしますか？')
    expect(dialog.textContent).toContain('7日')
    expect(dialog.textContent).toContain('進行中の相談は終了')
    expect(suspendUser).not.toHaveBeenCalled()

    fireEvent.click(within(dialog).getByRole('button', { name: '利用停止にする' }))

    await waitFor(() =>
      expect(suspendUser).toHaveBeenCalledWith(9, {
        reason: 'HARASSMENT',
        days: 7,
        memo: 'メモ',
        reportCaseId: null,
        resolveReport: false,
      }),
    )
    expect(findModerationUser).toHaveBeenCalledWith('ren_027')
    await screen.findByText('利用停止中')
  })

  it('期間の既定は1日で、無期限を選んだら days は null', async () => {
    findModerationUser.mockResolvedValue(user())
    suspendUser.mockResolvedValue(SUSPENDED)
    renderPage()
    await searchUser('ren_027')
    const panel = screen.getByRole('region', { name: '@ren_027' })
    // 既定は一番軽い1日（無期限は、わざわざ選んだときだけにする）
    expect((within(panel).getByLabelText('期間') as HTMLSelectElement).value).toBe('1')

    fireEvent.change(within(panel).getByLabelText('理由'), { target: { value: 'SPAM' } })
    fireEvent.change(within(panel).getByLabelText('期間'), { target: { value: '' } })
    fireEvent.click(within(panel).getByRole('button', { name: '利用停止にする' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '利用停止にする' }))

    await waitFor(() => expect(suspendUser).toHaveBeenCalledWith(9, expect.objectContaining({ days: null })))
  })

  it('理由を選ばなければ送らない', async () => {
    findModerationUser.mockResolvedValue(user())
    renderPage()
    await searchUser('ren_027')
    const panel = screen.getByRole('region', { name: '@ren_027' })

    fireEvent.click(within(panel).getByRole('button', { name: '利用停止にする' }))

    expect(screen.queryByRole('dialog')).toBeNull()
    expect(within(panel).getByRole('alert').textContent).toContain('理由を選んでください')
  })

  it('利用停止中なら、理由・期限・履歴（管理者・メモ）を出し、確認してから解除する', async () => {
    findModerationUser.mockResolvedValue(SUSPENDED)
    unsuspendUser.mockResolvedValue(user())
    renderPage()
    await searchUser('ren_027')
    const panel = screen.getByRole('region', { name: '@ren_027' })

    expect(panel.textContent).toContain('利用停止中')
    expect(panel.textContent).toContain('嫌がらせ・誹謗中傷')
    expect(panel.textContent).toContain('2026/10/19 10:00 まで')
    expect(panel.textContent).toContain('admin_k')
    expect(panel.textContent).toContain('何度も送っている')
    expect(within(panel).queryByRole('button', { name: '利用停止にする' })).toBeNull()

    fireEvent.click(within(panel).getByRole('button', { name: '利用停止を解除する' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog.textContent).toContain('終了した相談は戻りません')
    fireEvent.click(within(dialog).getByRole('button', { name: '解除する' }))

    await waitFor(() => expect(unsuspendUser).toHaveBeenCalledWith(9, ''))
  })

  it('管理者は利用停止にできないことを出し、ボタンを出さない', async () => {
    findModerationUser.mockResolvedValue(user({ admin: true }))
    renderPage()
    await searchUser('ren_027')
    const panel = screen.getByRole('region', { name: '@ren_027' })

    expect(panel.textContent).toContain('管理者は利用停止にできません')
    expect(within(panel).queryByRole('button', { name: '利用停止にする' })).toBeNull()
  })

  it('見つからなければ、その旨を出す', async () => {
    findModerationUser.mockRejectedValue(new ApiError(404, null, 'x'))
    renderPage()

    fireEvent.change(screen.getByLabelText('ユーザー名'), { target: { value: 'nobody' } })
    fireEvent.click(screen.getByRole('button', { name: 'ユーザーを探す' }))

    expect((await screen.findByRole('alert')).textContent).toContain('見つかりません')
  })
})

describe('投稿の非公開化', () => {
  it('投稿 ID で探し、投稿に使える理由だけを選べ、確認してから非公開にする', async () => {
    fetchModerationPost.mockResolvedValue(postState())
    hidePost.mockResolvedValue(postState({ hidden: true, hiddenReason: 'SPAM', hiddenReasonLabel: 'スパム・宣伝' }))
    renderPage()
    await searchPost('5')
    const panel = await screen.findByRole('region', { name: '秋のコーデ' })

    const options = [...within(panel).getByLabelText('理由').querySelectorAll('option')].map((o) => o.textContent)
    expect(options).toContain('他人の写真の無断転載')
    expect(options).not.toContain('なりすまし')

    fireEvent.change(within(panel).getByLabelText('理由'), { target: { value: 'SPAM' } })
    fireEvent.click(within(panel).getByRole('button', { name: '非公開にする' }))
    const dialog = await screen.findByRole('dialog')
    expect(dialog.textContent).toContain('投稿者本人には見えます')
    fireEvent.click(within(dialog).getByRole('button', { name: '非公開にする' }))

    await waitFor(() =>
      expect(hidePost).toHaveBeenCalledWith(5, { reason: 'SPAM', memo: '', reportCaseId: null, resolveReport: false }),
    )
    expect(fetchModerationPost).toHaveBeenCalledWith(5)
    await screen.findByText('非公開')
  })

  it('非公開なら「公開に戻す」を出す。失敗（409 など）したらサーバーの文言を出す', async () => {
    fetchModerationPost.mockResolvedValue(postState({ hidden: true, hiddenReason: 'SPAM', hiddenReasonLabel: 'スパム・宣伝' }))
    unhidePost.mockRejectedValue(new ApiError(409, { message: 'この投稿は非公開ではありません。', errors: {} }, 'この投稿は非公開ではありません。'))
    renderPage()
    await searchPost('5')
    const panel = await screen.findByRole('region', { name: '秋のコーデ' })

    fireEvent.click(within(panel).getByRole('button', { name: '公開に戻す' }))
    fireEvent.click(within(await screen.findByRole('dialog')).getByRole('button', { name: '公開に戻す' }))

    expect((await within(panel).findByRole('alert')).textContent).toContain('この投稿は非公開ではありません。')
  })

  it('数字でない投稿 ID では探さない', async () => {
    renderPage()

    await searchPost('abc')

    expect(fetchModerationPost).not.toHaveBeenCalled()
    expect(screen.getByRole('alert').textContent).toContain('数字')
  })
})

describe('措置中の一覧', () => {
  it('非公開の投稿と利用停止中のユーザーを出し、押すとその対象を開く', async () => {
    fetchModerationUser.mockResolvedValue(user({ userId: 8, username: 'mio' }))
    const { container } = renderPage()

    await screen.findByText('非公開の投稿')
    expect(container.textContent).toContain('@mio')
    expect(container.textContent).toContain('無期限')

    fireEvent.click(screen.getByRole('button', { name: '@mio' }))

    await screen.findByRole('heading', { name: '@mio' })
    expect(fetchModerationUser).toHaveBeenCalledWith(8)
  })
})
