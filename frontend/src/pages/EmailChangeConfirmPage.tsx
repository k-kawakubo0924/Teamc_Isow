import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { confirmEmailChange } from '../api/account'
import { useAuth } from '../auth/authContext'
import { AuthLogo } from './AuthLogo'
import { ErrorBanner } from './ErrorBanner'
import './auth.css'

/**
 * メールアドレスの変更の確認（/email-change/confirm#token=…。docs/settings.md「メールアドレスの変更」）。確認メールのリンクから開く。
 *
 * - 鍵は URL の # の後ろにある。読んだら URL から消す
 * - 開いただけでは完了しない（メールのリンクを自動で開く仕組みがあっても、勝手に完了しないように）。ボタンを押したときだけ完了する
 * - ログインしていない端末でも完了できる。完了するとすべての端末がログアウトになるため、この端末もログアウトしてログイン画面へ移る
 */
function EmailChangeConfirmPage() {
  const location = useLocation()
  const navigate = useNavigate()
  const { logout } = useAuth()
  const [token] = useState(() => new URLSearchParams(location.hash.replace(/^#/, '')).get('token'))
  const [error, setError] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  useEffect(() => {
    if (location.hash !== '') navigate(location.pathname, { replace: true })
  }, [location.hash, location.pathname, navigate])

  const handleConfirm = async () => {
    if (!token || submitting) return
    setSubmitting(true)
    setError(null)
    try {
      await confirmEmailChange(token)
      logout()
      navigate('/login', {
        replace: true,
        state: { notice: 'メールアドレスを変更しました。新しいメールアドレスでログインしてください。' },
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : '変更できませんでした。時間をおいて再度お試しください。')
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <AuthLogo spaced />
      <h2 className="auth-subtitle">メールアドレスの変更</h2>
      {!token ? (
        <ErrorBanner message="リンクが正しくありません。確認メールのリンクをもう一度開いてください。" />
      ) : (
        <>
          {error && <ErrorBanner message={error} />}
          <p className="auth-note">
            下のボタンを押すと、このメールアドレスへの変更が完了します。完了すると、すべての端末からログアウトします。
          </p>
          <div className="auth-actions">
            <button
              type="button"
              className="auth-button auth-button-primary auth-button-wide"
              disabled={submitting}
              onClick={handleConfirm}
            >
              {submitting ? '変更しています...' : 'メールアドレスの変更を完了する'}
            </button>
          </div>
        </>
      )}
    </main>
  )
}

export default EmailChangeConfirmPage
