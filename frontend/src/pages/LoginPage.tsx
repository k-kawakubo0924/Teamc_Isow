import { useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { login } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/authContext'
import { INVALID_INPUT_MESSAGE, normalizeEmail, validateLogin, type LoginForm } from '../validation/authRules'
import { ErrorBanner } from './ErrorBanner'
import './auth.css'

type LoginField = keyof LoginForm

/**
 * ログイン（docs/auth.md、design/Login.png・Loginauthenticationfailed.png）。
 * 成功したらトークンを保存し、ホーム画面へ遷移する。
 */
function LoginPage() {
  const navigate = useNavigate()
  const { login: saveLogin } = useAuth()
  const location = useLocation()
  const registered = (location.state as { registered?: boolean } | null)?.registered === true

  const [form, setForm] = useState<LoginForm>({ email: '', password: '' })
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<LoginField, string>>>({})
  // 認証失敗時は、どちらが違うかを明かさないため両方の欄を赤くする（欄の下の警告文は出さない）
  const [credentialsRejected, setCredentialsRejected] = useState(false)
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)
  const [showForgotNotice, setShowForgotNotice] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  const handleChange = (name: LoginField, value: string) => {
    setForm((prev) => ({ ...prev, [name]: value }))
    setFieldErrors((prev) => {
      if (!(name in prev)) return prev
      const { [name]: _removed, ...rest } = prev
      return rest
    })
  }

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    setCredentialsRejected(false)

    const errors = validateLogin(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setBannerMessage(INVALID_INPUT_MESSAGE)
      return
    }

    setSubmitting(true)
    setBannerMessage(null)
    try {
      const { token, expiresAt } = await login({ email: normalizeEmail(form.email), password: form.password })
      saveLogin(token, expiresAt)
      // 「戻る」でログイン画面に戻らないよう、履歴を置き換える
      navigate('/', { replace: true })
    } catch (err) {
      if (err instanceof ApiError && err.status === 401) {
        setCredentialsRejected(true)
      } else if (err instanceof ApiError && err.status === 400 && err.body) {
        setFieldErrors(err.body.errors)
      }
      setBannerMessage(err instanceof Error ? err.message : String(err))
    } finally {
      setSubmitting(false)
    }
  }

  const inputClassName = (name: LoginField) =>
    `auth-input${credentialsRejected || fieldErrors[name] ? ' auth-input-error' : ''}`

  return (
    <main className="auth-page">
      <h1 className="auth-logo auth-logo-spaced">ISHO</h1>

      {bannerMessage ? (
        <ErrorBanner message={bannerMessage} />
      ) : (
        registered && (
          <div className="auth-banner auth-banner-success" role="status">
            <p>登録が完了しました。ログインしてください。</p>
          </div>
        )
      )}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label className="auth-label" htmlFor="login-email">
            メールアドレス
          </label>
          <input
            id="login-email"
            className={inputClassName('email')}
            type="email"
            placeholder="example@mail.com"
            autoComplete="email"
            value={form.email}
            onChange={(e) => handleChange('email', e.target.value)}
            aria-invalid={credentialsRejected || fieldErrors.email ? true : undefined}
            aria-describedby={fieldErrors.email ? 'login-email-error' : undefined}
          />
          {fieldErrors.email && (
            <p className="auth-field-error" id="login-email-error">
              {fieldErrors.email}
            </p>
          )}
        </div>

        <div className="auth-field auth-field-tight">
          <label className="auth-label" htmlFor="login-password">
            パスワード
          </label>
          <input
            id="login-password"
            className={inputClassName('password')}
            type="password"
            placeholder="パスワードを入力"
            autoComplete="current-password"
            value={form.password}
            onChange={(e) => handleChange('password', e.target.value)}
            aria-invalid={credentialsRejected || fieldErrors.password ? true : undefined}
            aria-describedby={fieldErrors.password ? 'login-password-error' : undefined}
          />
          {fieldErrors.password && (
            <p className="auth-field-error" id="login-password-error">
              {fieldErrors.password}
            </p>
          )}
        </div>

        {/* パスワードリセットは未実装（docs/auth.md「未確定・要確認」参照） */}
        <div className="auth-forgot">
          <button type="button" className="auth-text-button" onClick={() => setShowForgotNotice(true)}>
            パスワードをお忘れですか？
          </button>
          {showForgotNotice && (
            <p className="auth-forgot-notice" role="status">
              この機能は準備中です
            </p>
          )}
        </div>

        <div className="auth-actions">
          <button type="submit" className="auth-button auth-button-primary auth-button-wide" disabled={submitting}>
            {submitting ? 'ログイン中...' : 'ログイン'}
          </button>
        </div>
      </form>

      <p className="auth-footer auth-footer-spaced">アカウントをお持ちでない方</p>
      <Link to="/register" className="auth-button auth-button-outline">
        新規登録
      </Link>
    </main>
  )
}

export default LoginPage
