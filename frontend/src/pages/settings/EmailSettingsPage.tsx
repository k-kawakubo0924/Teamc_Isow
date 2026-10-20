import { useEffect, useRef, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { requestEmailChange } from '../../api/account'
import { me } from '../../api/auth'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import { emailRuleError, normalizeEmail } from '../../validation/authRules'
import { PasswordInput } from '../PasswordInput'
import { goBack } from './goBack'
import './settings.css'

type Field = 'newEmail' | 'confirmEmail' | 'currentPassword'

/**
 * メールアドレス再設定（/settings/email。docs/settings.md「メールアドレスの変更」、design/ResetMailadress.png）。詳細設定から開く。
 *
 * - 今のアドレスを出し、新しいアドレス・確認用・現在のパスワードを入れて、右上の「完了」で申し込む
 * - 現在のパスワードの欄は設計画像にない。メールアドレスの変更はアカウントの所有権を移す操作で、
 *   開いたままの端末を触られただけで奪われるのを防ぐために足した
 * - 新しいアドレスに届いたリンクを開くと変更が完了する。サーバーは、アドレスがすでに使われていても同じ文言を返す
 */
function EmailSettingsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const [currentEmail, setCurrentEmail] = useState<string | null>(null)
  const [form, setForm] = useState({ newEmail: '', confirmEmail: '', currentPassword: '' })
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [message, setMessage] = useState<{ text: string; ok: boolean } | null>(null)
  const [saving, setSaving] = useState(false)
  const messageRef = useRef<HTMLParagraphElement>(null)

  // 「完了」は上に貼り付いたヘッダーにあり、下の欄まで動かしたまま押せる。フォームの上の文言が画面の外に出たままにならないよう、そこへ動かす
  useEffect(() => {
    if (message) messageRef.current?.scrollIntoView?.({ block: 'nearest' })
  }, [message])

  useEffect(() => {
    if (!token) return
    let cancelled = false
    me(token)
      .then((user) => {
        if (!cancelled) setCurrentEmail(user.email)
      })
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [token])

  const set = (field: Field, value: string) => setForm((prev) => ({ ...prev, [field]: value }))

  const handleSave = async () => {
    if (!token || saving) return
    const newEmail = normalizeEmail(form.newEmail)
    const confirmEmail = normalizeEmail(form.confirmEmail)
    const next: Partial<Record<Field, string>> = {}
    const emailError = emailRuleError(newEmail)
    if (emailError) next.newEmail = emailError
    if (confirmEmail === '') next.confirmEmail = '確認用メールアドレスを入力してください'
    else if (!emailError && newEmail !== confirmEmail) next.confirmEmail = '確認用メールアドレスが一致しません'
    if (form.currentPassword === '') next.currentPassword = '現在のパスワードを入力してください'
    setErrors(next)
    setMessage(null)
    if (Object.keys(next).length > 0) return

    setSaving(true)
    try {
      const result = await requestEmailChange(form.currentPassword, newEmail, confirmEmail, token)
      setMessage({ text: result.message, ok: true })
      setForm({ newEmail: '', confirmEmail: '', currentPassword: '' })
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.body && Object.keys(err.body.errors).length > 0) {
        setErrors(err.body.errors)
      } else {
        setMessage({ text: err instanceof Error ? err.message : '申し込めませんでした。時間をおいて再度お試しください。', ok: false })
      }
    } finally {
      setSaving(false)
    }
  }

  return (
    <main className="settings-page">
      <header className="settings-header">
        <button type="button" className="settings-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/settings')}>
          ‹
        </button>
        <h1 className="settings-title settings-title-center">メールアドレス再設定</h1>
        <button type="button" className="settings-save" disabled={saving} onClick={handleSave}>
          完了
        </button>
      </header>
      <div className="account-form">
        {message && (
          <p className={`account-message${message.ok ? '' : ' account-message-error'}`} role={message.ok ? 'status' : 'alert'} ref={messageRef}>
            {message.text}
          </p>
        )}
        <div className="account-field">
          <p className="account-label">現在のメールアドレス</p>
          <p className="account-readonly">{currentEmail ?? '読み込み中...'}</p>
        </div>
        <div className="account-field">
          <label className="account-label" htmlFor="settings-new-email">
            新しいメールアドレス
          </label>
          <input
            id="settings-new-email"
            type="email"
            className={`account-input${errors.newEmail ? ' account-input-error' : ''}`}
            placeholder="新しいメールアドレスを入力"
            autoComplete="email"
            value={form.newEmail}
            aria-invalid={errors.newEmail ? true : undefined}
            onChange={(e) => set('newEmail', e.target.value)}
          />
          {errors.newEmail ? (
            <p className="account-error">{errors.newEmail}</p>
          ) : (
            <p className="account-hint">入力したアドレスに確認メールを送ります</p>
          )}
        </div>
        <div className="account-field">
          <label className="account-label" htmlFor="settings-confirm-email">
            確認用メールアドレス
          </label>
          <input
            id="settings-confirm-email"
            type="email"
            className={`account-input${errors.confirmEmail ? ' account-input-error' : ''}`}
            placeholder="もう一度入力"
            autoComplete="email"
            value={form.confirmEmail}
            aria-invalid={errors.confirmEmail ? true : undefined}
            onChange={(e) => set('confirmEmail', e.target.value)}
          />
          {errors.confirmEmail && <p className="account-error">{errors.confirmEmail}</p>}
        </div>
        {/* 設計画像にない欄（docs/settings.md「設計画像と変えた点：現在のパスワードを求める」） */}
        <div className="account-field">
          <label className="account-label" htmlFor="settings-email-password">
            現在のパスワード
          </label>
          <PasswordInput
            id="settings-email-password"
            className={`account-input${errors.currentPassword ? ' account-input-error' : ''}`}
            placeholder="現在のパスワードを入力"
            autoComplete="current-password"
            value={form.currentPassword}
            aria-invalid={errors.currentPassword ? true : undefined}
            onChange={(e) => set('currentPassword', e.target.value)}
          />
          {errors.currentPassword && <p className="account-error">{errors.currentPassword}</p>}
        </div>
        <p className="account-hint">
          確認メールのリンクを開くまでは、今のメールアドレスでログインできます。変更が完了すると、すべての端末からログアウトします。
        </p>
      </div>
    </main>
  )
}

export default EmailSettingsPage
