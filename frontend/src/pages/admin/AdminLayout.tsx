import { useEffect, useState } from 'react'
import { Link, NavLink, Outlet } from 'react-router'
import { getAdminMe } from '../../api/admin'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import NotFoundPage from '../NotFoundPage'
import './admin.css'

/** 管理画面の行き先。中身の画面はステップごとに足す（docs/admin.md） */
const MENU = [
  { label: 'マスタ管理', to: '/admin/masters' },
  { label: 'お知らせ', to: '/admin/announcements' },
  { label: '操作ログ', to: '/admin/operation-logs' },
] as const

type CheckState =
  | { status: 'checking' }
  | { status: 'admin'; username: string }
  | { status: 'notAdmin' }
  | { status: 'error'; message: string }

/**
 * 管理画面の枠（/admin 以下。docs/admin.md）。PC で使う前提で、左に行き先を並べる。利用者アプリの下部ナビゲーションは出さない。
 *
 * 開いたときに、ログイン中のユーザーが管理者かをサーバーに確かめる（GET /api/admin/me）。
 * 管理者でなければ、存在しない URL と同じ「ページが見つかりません」を出す（管理画面があることを分からないようにするため）。
 * ここでの判定は表示の出し分けだけで、守りはサーバー側で行う（/api/admin/** は管理者でなければ 404）
 */
function AdminLayout() {
  const { token } = useAuth()
  const [check, setCheck] = useState<CheckState>({ status: 'checking' })

  useEffect(() => {
    // RequireAuth の中に置くため、ここではログイン済み（token あり）
    if (token === null) return
    let cancelled = false
    getAdminMe(token)
      .then((me) => {
        if (!cancelled) setCheck({ status: 'admin', username: me.username })
      })
      .catch((err: unknown) => {
        if (cancelled) return
        if (err instanceof ApiError && err.status === 404) {
          setCheck({ status: 'notAdmin' })
        } else if (err instanceof ApiError && err.status === 401) {
          // トークンの期限切れなど。client がログアウトし、RequireAuth がログイン画面へ移すため、何も出さない
        } else {
          setCheck({ status: 'error', message: err instanceof Error ? err.message : '読み込みに失敗しました。' })
        }
      })
    return () => {
      cancelled = true
    }
  }, [token])

  switch (check.status) {
    case 'checking':
      // 確かめている間は何も出さない（一般の利用者に管理画面の枠が一瞬見えないように）
      return null
    case 'notAdmin':
      return <NotFoundPage />
    case 'error':
      // 管理者かどうか分からないため、管理画面に関わる文言・部品を出さない（ページが見つからない画面と同じ見た目）
      return (
        <main className="not-found-page">
          <p className="not-found-text" role="alert">
            {check.message}
          </p>
        </main>
      )
    case 'admin':
      return (
        <div className="admin-layout">
          <aside className="admin-sidebar">
            <header className="admin-header">
              <p className="admin-logo">ISHO 管理画面</p>
              <p className="admin-user">{check.username}</p>
            </header>
            <nav className="admin-nav" aria-label="管理メニュー">
              <ul className="admin-nav-list">
                {MENU.map((item) => (
                  <li key={item.to}>
                    <NavLink
                      to={item.to}
                      className={({ isActive }) => (isActive ? 'admin-nav-link admin-nav-link-active' : 'admin-nav-link')}
                    >
                      {item.label}
                    </NavLink>
                  </li>
                ))}
              </ul>
            </nav>
            <Link to="/" className="admin-back">
              アプリに戻る
            </Link>
          </aside>
          <main className="admin-main">
            <Outlet />
          </main>
        </div>
      )
  }
}

export default AdminLayout
