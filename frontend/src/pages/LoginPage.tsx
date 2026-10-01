import { Link, useLocation } from 'react-router'
import './auth.css'

/**
 * ログイン画面（仮）。ステップ6で design/Login.png に合わせて実装する。
 * 現時点では、新規登録完了後の遷移先としてのみ使う。
 */
function LoginPage() {
  const location = useLocation()
  const registered = (location.state as { registered?: boolean } | null)?.registered === true

  return (
    <main className="auth-page">
      <h1 className="auth-logo">ISHO</h1>

      {registered && (
        <div className="auth-banner auth-banner-success" role="status">
          <p>登録が完了しました。ログインしてください。</p>
        </div>
      )}

      <p className="auth-footer">ログイン画面は準備中です。</p>
      <p className="auth-footer">
        アカウントをお持ちでない方
        <Link to="/register" className="auth-link">
          新規登録
        </Link>
      </p>
    </main>
  )
}

export default LoginPage
