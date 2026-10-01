// =============================================================================
// 【仮実装】認証の動作確認用のホーム画面です。
// ログイン後に保存したトークンで GET /api/auth/me を呼べることを確認するためだけに置いています。
// ホーム機能（docs/home.md、design/home.png）の担当者が、この画面を丸ごと置き換える前提です。
// 置き換えるときは、App.tsx のルート（path="/"）はそのまま使ってください。
//
// ログアウトボタンも仮置きです。仕様上の置き場所は詳細設定のメニュー（docs/settings.md）なので、
// 詳細設定の担当者は useAuth().logout() を呼んでから navigate('/login', { replace: true }) してください。
// =============================================================================

import { useEffect, useState } from 'react'
import { useNavigate } from 'react-router'
import { me, type UserResponse } from '../api/auth'
import { useAuth } from '../auth/authContext'
import './auth.css'

type MeState =
  | { phase: 'loading' }
  | { phase: 'success'; user: UserResponse }
  | { phase: 'error'; message: string }

function HomePage() {
  const navigate = useNavigate()
  const { token, logout } = useAuth()
  const [state, setState] = useState<MeState>({ phase: 'loading' })

  useEffect(() => {
    // この画面は RequireAuth の内側にあるため、token は必ずある
    if (!token) return

    let cancelled = false
    // 401 の場合は AuthProvider がログアウトし、RequireAuth がログイン画面へ移動させる
    me(token)
      .then((user) => {
        if (!cancelled) setState({ phase: 'success', user })
      })
      .catch((err: unknown) => {
        if (!cancelled) setState({ phase: 'error', message: err instanceof Error ? err.message : String(err) })
      })
    return () => {
      cancelled = true
    }
  }, [token])

  const handleLogout = () => {
    logout()
    navigate('/login', { replace: true })
  }

  return (
    <main className="auth-page">
      <h1 className="auth-logo">ISHO</h1>
      <p className="auth-subtitle">ホーム（仮）</p>

      {state.phase === 'loading' && <p className="auth-footer">確認中...</p>}
      {state.phase === 'success' && <p className="auth-footer">@{state.user.username} でログイン中</p>}
      {state.phase === 'error' && <p className="auth-footer">{state.message}</p>}

      <div className="auth-home-actions">
        <button type="button" className="auth-button auth-button-outline" onClick={handleLogout}>
          ログアウト
        </button>
      </div>
    </main>
  )
}

export default HomePage
