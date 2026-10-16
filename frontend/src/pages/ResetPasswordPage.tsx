import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate } from 'react-router'
import { confirmPasswordReset } from '../api/auth'
import { ApiError } from '../api/client'
import { useAuth } from '../auth/authContext'
import { passwordRuleError } from '../validation/authRules'
import { ErrorBanner } from './ErrorBanner'
import { PasswordInput } from './PasswordInput'
import './auth.css'

type Field = 'newPassword' | 'confirmPassword'

/**
 * 新しいパスワードを設定する画面（/reset-password#token=…。docs/auth.md「パスワードの再設定」）。再設定のメールのリンクから開く。
 *
 * - 鍵は URL の # の後ろにある（サーバーに送られないため、アクセスログ・リンク元の情報に残らない）。読んだら URL から消す
 * - 開いただけでは何もしない（メールのリンクを自動で開く仕組みがあっても、勝手に使われないように）。入力して押したときだけ使う
 * - 設定すると、すべての端末がログアウトになる。この端末もログアウトして、ログイン画面へ移る
 * - ログインの有無で振り分けない（ログイン中に開いても使えるように。App.tsx）
 */
function ResetPasswordPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()
  // 最初に開いたときの # の後ろから鍵を読む（URL から消した後も、画面を閉じるまでは持っておく）
  const [token] = useState(() => new URLSearchParams(location.hash.replace(/^#/, '')).get('token'))
  const [form, setForm] = useState({ newPassword: '', confirmPassword: '' })
  const [fieldErrors, setFieldErrors] = useState<Partial<Record<Field, string>>>({})
  const [error, setError] = useState<string | null>(null)
  const [linkUnusable, setLinkUnusable] = useState(false)
  const [submitting, setSubmitting] = useState(false)

  // 鍵を URL から消す（履歴にも残さない）
  useEffect(() => {
    if (location.hash !== '') navigate(location.pathname, { replace: true })
  }, [location.hash, location.pathname, navigate])

  const handleSubmit = async (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    if (!token) return
    const errors: Partial<Record<Field, string>> = {}
    const ruleError = passwordRuleError(form.newPassword)
    if (ruleError) errors.newPassword = ruleError
    else if (form.newPassword !== form.confirmPassword) errors.confirmPassword = '確認用パスワードが一致しません'
    setFieldErrors(errors)
    setError(null)
    if (Object.keys(errors).length > 0) return

    setSubmitting(true)
    try {
      await confirmPasswordReset(token, form.newPassword)
      // サーバーでトークンの世代が進み、この端末のトークンも使えなくなっているため、ここでもログアウトする
      logout()
      navigate('/login', {
        replace: true,
        state: { notice: 'パスワードを再設定しました。新しいパスワードでログインしてください。' },
      })
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.body && Object.keys(err.body.errors).length > 0) {
        setFieldErrors(err.body.errors)
      } else {
        setLinkUnusable(err instanceof ApiError && err.status === 400)
        setError(err instanceof Error ? err.message : '再設定できませんでした。時間をおいて再度お試しください。')
      }
    } finally {
      setSubmitting(false)
    }
  }

  const againLink = (
    <p className="auth-footer auth-footer-spaced">
      <Link to="/forgot-password" className="auth-link">
        再設定のメールをもう一度送る
      </Link>
    </p>
  )

  if (!token) {
    return (
      <main className="auth-page">
        <h1 className="auth-logo auth-logo-spaced">ISHO</h1>
        <h2 className="auth-subtitle">パスワードの再設定</h2>
        <ErrorBanner message="リンクが正しくありません。メールのリンクをもう一度開くか、再設定のメールをもう一度送ってください。" />
        {againLink}
      </main>
    )
  }

  return (
    <main className="auth-page">
      <h1 className="auth-logo auth-logo-spaced">ISHO</h1>
      <h2 className="auth-subtitle">パスワードの再設定</h2>

      {error && <ErrorBanner message={error} />}

      <form className="auth-form" onSubmit={handleSubmit} noValidate>
        <div className="auth-field">
          <label className="auth-label" htmlFor="reset-new-password">
            新しいパスワード
          </label>
          <PasswordInput
            id="reset-new-password"
            className={`auth-input${fieldErrors.newPassword ? ' auth-input-error' : ''}`}
            placeholder="新しいパスワードを入力"
            autoComplete="new-password"
            value={form.newPassword}
            onChange={(e) => setForm((prev) => ({ ...prev, newPassword: e.target.value }))}
            aria-invalid={fieldErrors.newPassword ? true : undefined}
          />
          {fieldErrors.newPassword ? (
            <p className="auth-field-error">{fieldErrors.newPassword}</p>
          ) : (
            <p className="auth-note">8文字以上、英字と数字を含めてください</p>
          )}
        </div>
        <div className="auth-field">
          <label className="auth-label" htmlFor="reset-confirm-password">
            確認用パスワード
          </label>
          <PasswordInput
            id="reset-confirm-password"
            className={`auth-input${fieldErrors.confirmPassword ? ' auth-input-error' : ''}`}
            placeholder="もう一度入力"
            autoComplete="new-password"
            value={form.confirmPassword}
            onChange={(e) => setForm((prev) => ({ ...prev, confirmPassword: e.target.value }))}
            aria-invalid={fieldErrors.confirmPassword ? true : undefined}
          />
          {fieldErrors.confirmPassword && <p className="auth-field-error">{fieldErrors.confirmPassword}</p>}
        </div>
        <p className="auth-note">再設定すると、ログイン中のすべての端末からログアウトします。</p>
        <div className="auth-actions">
          <button type="submit" className="auth-button auth-button-primary auth-button-wide" disabled={submitting}>
            {submitting ? '再設定しています...' : 'パスワードを再設定する'}
          </button>
        </div>
      </form>

      {linkUnusable && againLink}
    </main>
  )
}

export default ResetPasswordPage
