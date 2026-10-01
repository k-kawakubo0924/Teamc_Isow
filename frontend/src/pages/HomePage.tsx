// =============================================================================
// 【仮実装】認証の動作確認用のホーム画面です。
// ログイン後に保存したトークンで GET /api/auth/me を呼べることを確認するためだけに置いています。
// ホーム機能（docs/home.md、design/home.png）の担当者が、この画面を丸ごと置き換える前提です。
// 置き換えるときは、App.tsx のルート（path="/"）はそのまま使ってください。
// =============================================================================

import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { me, type UserResponse } from '../api/auth'
import { getToken } from '../auth/tokenStorage'
import './auth.css'

type MeState =
  | { phase: 'loading' }
  | { phase: 'success'; user: UserResponse }
  | { phase: 'error'; message: string }

function HomePage() {
  const [state, setState] = useState<MeState>(() =>
    getToken() ? { phase: 'loading' } : { phase: 'error', message: 'ログインしていません。' },
  )

  useEffect(() => {
    const token = getToken()
    if (!token) return

    let cancelled = false
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
  }, [])

  return (
    <main className="auth-page">
      <h1 className="auth-logo">ISHO</h1>
      <p className="auth-subtitle">ホーム（仮）</p>

      {state.phase === 'loading' && <p className="auth-footer">確認中...</p>}
      {state.phase === 'success' && <p className="auth-footer">@{state.user.username} でログイン中</p>}
      {state.phase === 'error' && (
        <p className="auth-footer">
          {state.message}
          <Link to="/login" className="auth-link">
            ログイン
          </Link>
        </p>
      )}
    </main>
  )
}

export default HomePage
