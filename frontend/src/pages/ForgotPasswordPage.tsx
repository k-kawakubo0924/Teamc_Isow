import { useState, type FormEvent } from 'react'
import { Link } from 'react-router'
import { requestPasswordReset } from '../api/auth'
import { emailRuleError, normalizeEmail } from '../validation/authRules'
import { AuthLogo } from './AuthLogo'
import { ErrorBanner } from './ErrorBanner'
import './auth.css'

/**
 * パスワードの再設定のメールを頼む画面（/forgot-password。docs/auth.md「パスワードの再設定」）。ログイン画面の「パスワードをお忘れですか？」から開く。
 * 画面画像はない（ログイン画面の見た目に合わせた）。
 * サーバーは登録の有無にかかわらず同じ文言を返すため、その文言をそのまま出す（登録済みかどうかを画面からも分からないように）
 */
function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [fieldError, setFieldError] = useState<string | null>(null)
  const [message, setMessage] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const normalized = normalizeEmail(email)
    const ruleError = emailRuleError(normalized)
    setFieldError(ruleError)
    setError(null)
    if (ruleError !== null) return

    setSubmitting(true)
    try {
      const result = await requestPasswordReset(normalized)
      setMessage(result.message)
    } catch (err) {
      setError(err instanceof Error ? err.message : '送信できませんでした。時間をおいて再度お試しください。')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <AuthLogo spaced />
      <h2 className="auth-subtitle">パスワードの再設定</h2>

      {error && <ErrorBanner message={error} />}

      {message ? (
        <div className="auth-banner auth-banner-success" role="status">
          <p>{message}</p>
        </div>
      ) : (
        <form className="auth-form" onSubmit={handleSubmit} noValidate>
          <p className="auth-note">登録したメールアドレスを入力してください。パスワードを再設定するためのリンクを送ります。</p>
          <div className="auth-field">
            <label className="auth-label" htmlFor="forgot-email">
              メールアドレス
            </label>
            <input
              id="forgot-email"
              className={`auth-input${fieldError ? ' auth-input-error' : ''}`}
              type="email"
              placeholder="example@mail.com"
              autoComplete="email"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              aria-invalid={fieldError ? true : undefined}
              aria-describedby={fieldError ? 'forgot-email-error' : undefined}
            />
            {fieldError && (
              <p className="auth-field-error" id="forgot-email-error">
                {fieldError}
              </p>
            )}
          </div>
          <div className="auth-actions">
            <button type="submit" className="auth-button auth-button-primary auth-button-wide" disabled={submitting}>
              {submitting ? '送信中...' : '再設定のメールを送る'}
            </button>
          </div>
        </form>
      )}

      <p className="auth-footer auth-footer-spaced">
        <Link to="/login" className="auth-link">
          ログイン画面に戻る
        </Link>
      </p>
    </main>
  )
}

export default ForgotPasswordPage
