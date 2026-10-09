import { cleanup, render, screen } from '@testing-library/react'
import { MemoryRouter } from 'react-router'
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import App from './App'
import { ApiError } from './api/client'

/*
 * 画面の振り分け（App.tsx）の確認。
 * 各画面の中身は別のテストで確かめるため、ここでは画面を名前だけの部品に置き換え、
 * 「どの URL でどの画面が開くか」「下部ナビゲーションの中か外か」だけを確かめる。
 */

const { stub, auth, adminMe } = vi.hoisted(() => {
  const stubPage = (name: string) => () => <p data-testid="page">{name}</p>
  return {
    stub: stubPage,
    // ログイン状態はテストごとに切り替える
    auth: { isLoggedIn: true },
    adminMe: vi.fn(),
  }
})

vi.mock('./auth/authContext', () => ({
  useAuth: () => ({ token: auth.isLoggedIn ? 'token' : null, isLoggedIn: auth.isLoggedIn }),
}))
vi.mock('./api/admin', async (importOriginal) => ({
  ...(await importOriginal<typeof import('./api/admin')>()),
  getAdminMe: adminMe,
  // マスタ管理の画面の一覧は、ここでは読み込ませない（画面の中身は AdminMastersPage.test.tsx で確かめる）
  listMasters: () => new Promise(() => {}),
}))
vi.mock('./components/BottomNav', async () => {
  const { Outlet } = await import('react-router')
  return {
    TabLayout: () => (
      <div data-testid="tab-layout">
        <Outlet />
      </div>
    ),
  }
})
vi.mock('./pages/home/HomePage', () => ({ default: stub('ホーム') }))
vi.mock('./pages/post/PostDetailPage', () => ({ default: stub('投稿の詳細') }))
vi.mock('./pages/notification/NotificationPage', () => ({ default: stub('通知一覧') }))
vi.mock('./pages/search/SearchPage', () => ({ default: stub('検索') }))
vi.mock('./pages/search/SearchResultsPage', () => ({ default: stub('検索結果') }))
vi.mock('./pages/profile/ProfilePage', () => ({
  MyProfilePage: stub('自分のプロフィール'),
  UserProfilePage: stub('ユーザーのプロフィール'),
}))
vi.mock('./pages/follow/FollowListPage', () => ({
  FollowListPage: ({ kind }: { kind: string }) => <p data-testid="page">{`フォロー一覧:${kind}`}</p>,
}))
vi.mock('./pages/dm/DmListPage', () => ({ default: stub('DM一覧') }))
vi.mock('./pages/dm/DmRequestsPage', () => ({ default: stub('DMの申請') }))
vi.mock('./pages/dm/DmSentPage', () => ({ default: stub('DMの送信済み') }))
vi.mock('./pages/dm/DmChatPage', () => ({ default: stub('チャット') }))
vi.mock('./pages/post/PostPage', () => ({ default: stub('投稿作成') }))
vi.mock('./pages/settings/SettingsPage', () => ({ default: stub('詳細設定') }))
vi.mock('./pages/settings/ProfileEditPage', () => ({ default: stub('プロフィール編集') }))
vi.mock('./pages/LoginPage', () => ({ default: stub('ログイン') }))
vi.mock('./pages/SignUpPage', () => ({ default: stub('新規会員登録') }))
vi.mock('./pages/HealthCheckPage', () => ({ default: stub('ヘルスチェック') }))

beforeEach(() => {
  auth.isLoggedIn = true
  adminMe.mockReset()
})

afterEach(() => {
  cleanup()
})

function renderAt(path: string) {
  return render(
    <MemoryRouter initialEntries={[path]}>
      <App />
    </MemoryRouter>,
  )
}

describe('既存の利用者アプリの画面（ページが見つからない画面・管理画面の追加に巻き込まれていないこと）', () => {
  // [URL, 開く画面, 下部ナビゲーションの中か]
  const loggedInRoutes: [string, string, boolean][] = [
    ['/', 'ホーム', true],
    ['/posts/1', '投稿の詳細', true],
    ['/notifications', '通知一覧', true],
    ['/search', '検索', true],
    ['/search/results?q=a', '検索結果', true],
    ['/profile', '自分のプロフィール', true],
    ['/users/2', 'ユーザーのプロフィール', true],
    ['/users/2/followings', 'フォロー一覧:followings', true],
    ['/users/2/followers', 'フォロー一覧:followers', true],
    ['/dm', 'DM一覧', true],
    ['/dm/requests', 'DMの申請', true],
    ['/dm/sent', 'DMの送信済み', true],
    ['/dm/3', 'チャット', false],
    ['/post', '投稿作成', false],
    ['/settings', '詳細設定', false],
    ['/settings/profile', 'プロフィール編集', false],
    ['/health', 'ヘルスチェック', false],
  ]

  it.each(loggedInRoutes)('ログイン中に %s を開くと %s の画面が開く', (path, page, withTabs) => {
    renderAt(path)

    expect(screen.getByTestId('page').textContent).toBe(page)
    expect(screen.queryByTestId('tab-layout') !== null).toBe(withTabs)
    expect(screen.queryByText('ページが見つかりません')).toBeNull()
  })

  it.each([
    ['/login', 'ログイン'],
    ['/register', '新規会員登録'],
    ['/health', 'ヘルスチェック'],
  ])('未ログインで %s を開くと %s の画面が開く', (path, page) => {
    auth.isLoggedIn = false
    renderAt(path)

    expect(screen.getByTestId('page').textContent).toBe(page)
  })

  it.each(['/', '/posts/1', '/settings'])('未ログインで %s を開くとログイン画面へ移る', (path) => {
    auth.isLoggedIn = false
    renderAt(path)

    expect(screen.getByTestId('page').textContent).toBe('ログイン')
  })

  it('ログイン中にログイン画面を開くとホームへ移る', () => {
    renderAt('/login')

    expect(screen.getByTestId('page').textContent).toBe('ホーム')
  })
})

describe('ページが見つからない画面', () => {
  it.each(['/no-such-page', '/posts/1/extra', '/dm/3/extra', '/settings/unknown'])(
    'ログイン中に存在しない URL（%s）を開くと「ページが見つかりません」が出る',
    (path) => {
      const { container } = renderAt(path)

      expect(screen.getByRole('heading', { name: 'ページが見つかりません' })).toBeTruthy()
      expect(screen.getByRole('link', { name: 'ホームへ戻る' }).getAttribute('href')).toBe('/')
      // 下部ナビゲーションも、他の画面も出さない
      expect(screen.queryByTestId('tab-layout')).toBeNull()
      expect(screen.queryByTestId('page')).toBeNull()
      // 管理画面の存在が分かるものを出さない
      expect(container.innerHTML).not.toMatch(/admin|管理/)
    },
  )

  it('未ログインで存在しない URL を開くと、他の画面と同じくログイン画面へ移る', () => {
    auth.isLoggedIn = false
    renderAt('/no-such-page')

    expect(screen.getByTestId('page').textContent).toBe('ログイン')
  })
})

describe('管理画面（/admin）', () => {
  it('管理者なら管理画面の枠が出て、マスタ管理・お知らせ・操作ログへの行き先がある', async () => {
    adminMe.mockResolvedValue({ id: 1, username: 'admin' })
    const { container } = renderAt('/admin')

    const nav = await screen.findByRole('navigation', { name: '管理メニュー' })
    expect(screen.getByRole('link', { name: 'マスタ管理' }).getAttribute('href')).toBe('/admin/masters')
    expect(screen.getByRole('link', { name: 'お知らせ' }).getAttribute('href')).toBe('/admin/announcements')
    expect(screen.getByRole('link', { name: '操作ログ' }).getAttribute('href')).toBe('/admin/operation-logs')
    expect(nav.closest('.admin-layout')).not.toBeNull()
    // /admin を開いたら、最初の項目（マスタ管理）を開く
    expect(await screen.findByRole('heading', { name: 'マスタ管理' })).toBeTruthy()
    expect(screen.getByRole('link', { name: 'マスタ管理' }).getAttribute('aria-current')).toBe('page')
    // ログイン中の管理者が分かる
    expect(container.querySelector('.admin-header')?.textContent).toContain('admin')
    // 利用者アプリの下部ナビゲーションは出さない
    expect(screen.queryByTestId('tab-layout')).toBeNull()
    expect(adminMe).toHaveBeenCalledWith('token')
  })

  it.each([
    ['/admin/announcements', 'お知らせ'],
    ['/admin/operation-logs', '操作ログ'],
  ])('管理者が %s を開くと %s の画面が枠の中に出る', async (path, heading) => {
    adminMe.mockResolvedValue({ id: 1, username: 'admin' })
    renderAt(path)

    expect(await screen.findByRole('heading', { name: heading })).toBeTruthy()
    expect(screen.getByRole('link', { name: heading }).getAttribute('aria-current')).toBe('page')
  })

  it('管理者が管理画面の中の存在しない URL を開くと、枠の中に「ページが見つかりません」が出る', async () => {
    adminMe.mockResolvedValue({ id: 1, username: 'admin' })
    renderAt('/admin/no-such-page')

    expect(await screen.findByRole('heading', { name: 'ページが見つかりません' })).toBeTruthy()
    expect(screen.getByRole('navigation', { name: '管理メニュー' })).toBeTruthy()
  })

  it.each(['/admin', '/admin/masters', '/admin/no-such-page'])(
    'ログイン済みの一般利用者が %s を開くと、存在しない URL と同じ「ページが見つかりません」だけが出る',
    async (path) => {
      adminMe.mockRejectedValue(new ApiError(404, null, 'Not Found'))
      const { container } = renderAt(path)

      expect(await screen.findByRole('heading', { name: 'ページが見つかりません' })).toBeTruthy()
      expect(screen.queryByRole('navigation', { name: '管理メニュー' })).toBeNull()
      expect(container.querySelector('[class*="admin-"]')).toBeNull()
      expect(container.innerHTML).not.toMatch(/admin|管理/)
      // 存在しない URL を開いたときと、画面がまったく同じ（管理画面があることが画面から分からない）
      const adminHtml = container.innerHTML
      cleanup()
      expect(renderAt('/no-such-page').container.innerHTML).toBe(adminHtml)
    },
  )

  it('確かめている間は何も出さない（管理画面の枠も「ページが見つかりません」も出さない）', () => {
    adminMe.mockReturnValue(new Promise(() => {}))
    const { container } = renderAt('/admin')

    expect(container.innerHTML).toBe('')
  })

  it('確かめられなかった場合（接続できないなど）は、管理画面のことが分からない文言でエラーを出す', async () => {
    adminMe.mockRejectedValue(new ApiError(0, null, 'サーバーに接続できませんでした。時間をおいて再度お試しください。'))
    const { container } = renderAt('/admin')

    expect(await screen.findByText('サーバーに接続できませんでした。時間をおいて再度お試しください。')).toBeTruthy()
    expect(container.innerHTML).not.toMatch(/admin|管理/)
  })

  it('未ログインで /admin を開くと、他の画面と同じくログイン画面へ移る（管理者かどうかは確かめない）', () => {
    auth.isLoggedIn = false
    renderAt('/admin')

    expect(screen.getByTestId('page').textContent).toBe('ログイン')
    expect(adminMe).not.toHaveBeenCalled()
  })
})

describe('利用者アプリの画面から管理画面へのリンクを置かない', () => {
  it('ソースに /admin へのリンクがない（管理画面の部品と画面の振り分けを除く）', async () => {
    const sources = import.meta.glob(['./**/*.tsx', '!./**/*.test.tsx', '!./pages/admin/**', '!./App.tsx'], {
      query: '?raw',
      import: 'default',
      eager: true,
    }) as Record<string, string>

    expect(Object.keys(sources).length).toBeGreaterThan(10)
    for (const [file, source] of Object.entries(sources)) {
      expect(source, file).not.toMatch(/['"`]\/admin/)
    }
  })
})
