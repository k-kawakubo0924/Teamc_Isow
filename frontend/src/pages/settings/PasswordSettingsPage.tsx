import { useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { changePassword } from '../../api/account'
import { ApiError } from '../../api/client'
import { useAuth } from '../../auth/authContext'
import { passwordRuleError } from '../../validation/authRules'
import { PasswordInput } from '../PasswordInput'
import { goBack } from './goBack'
import './settings.css'

type Field = 'currentPassword' | 'newPassword' | 'confirmPassword'

/**
 * パスワード再設定（ログイン中の変更。/settings/password。docs/settings.md、design/ResetPassword.png）。詳細設定から開く。
 * 現在のパスワード・新しいパスワード・確認用パスワードを入れ、右上の「完了」で変える。
 * 変えるとほかの端末はログアウトになり、この端末は返ってきた新しいトークンでログインしたままにする
 */
function PasswordSettingsPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token, login } = useAuth()
  const [form, setForm] = useState({ currentPassword: '', newPassword: '', confirmPassword: '' })
  const [errors, setErrors] = useState<Partial<Record<Field, string>>>({})
  const [message, setMessage] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  const set = (field: Field, value: string) => setForm((prev) => ({ ...prev, [field]: value }))

  const handleSave = async () => {
    if (!token || saving) return
    const next: Partial<Record<Field, string>> = {}
    if (form.currentPassword === '') next.currentPassword = '現在のパスワードを入力してください'
    const ruleError = passwordRuleError(form.newPassword)
    if (ruleError) next.newPassword = ruleError
    else if (form.newPassword !== form.confirmPassword) next.confirmPassword = '確認用パスワードが一致しません'
    setErrors(next)
    setMessage(null)
    if (Object.keys(next).length > 0) return

    setSaving(true)
    try {
      const result = await changePassword(form.currentPassword, form.newPassword, token)
      // ほかの端末はログアウトになった。この端末は新しいトークンに置き換える
      login(result.token, result.expiresAt)
      navigate('/settings', { replace: true, state: { notice: 'パスワードを変更しました。ほかの端末はログアウトしました。' } })
    } catch (err) {
      if (err instanceof ApiError && err.status === 400 && err.body && Object.keys(err.body.errors).length > 0) {
        setErrors(err.body.errors)
      } else {
        setMessage(err instanceof Error ? err.message : '変更できませんでした。時間をおいて再度お試しください。')
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
        <h1 className="settings-title settings-title-center">パスワード再設定</h1>
        <button type="button" className="settings-save" disabled={saving} onClick={handleSave}>
          完了
        </button>
      </header>
      <div className="account-form">
        {message && (
          <p className="account-message account-message-error" role="alert">
            {message}
          </p>
        )}
        <PasswordField
          id="settings-current-password"
          label="現在のパスワード"
          value={form.currentPassword}
          error={errors.currentPassword}
          autoComplete="current-password"
          placeholder="現在のパスワードを入力"
          onChange={(v) => set('currentPassword', v)}
        />
        <PasswordField
          id="settings-new-password"
          label="新しいパスワード"
          value={form.newPassword}
          error={errors.newPassword}
          hint="8文字以上、英字と数字を含めてください"
          autoComplete="new-password"
          placeholder="新しいパスワードを入力"
          onChange={(v) => set('newPassword', v)}
        />
        <PasswordField
          id="settings-confirm-password"
          label="確認用パスワード"
          value={form.confirmPassword}
          error={errors.confirmPassword}
          autoComplete="new-password"
          placeholder="もう一度入力"
          onChange={(v) => set('confirmPassword', v)}
        />
        <p className="account-hint">変更すると、ほかの端末はすべてログアウトします。</p>
      </div>
    </main>
  )
}

function PasswordField({
  id,
  label,
  value,
  error,
  hint,
  autoComplete,
  placeholder,
  onChange,
}: {
  id: string
  label: string
  value: string
  error?: string
  hint?: string
  autoComplete: string
  placeholder: string
  onChange: (value: string) => void
}) {
  return (
    <div className="account-field">
      <label className="account-label" htmlFor={id}>
        {label}
      </label>
      <PasswordInput
        id={id}
        className={`account-input${error ? ' account-input-error' : ''}`}
        value={value}
        autoComplete={autoComplete}
        placeholder={placeholder}
        aria-invalid={error ? true : undefined}
        onChange={(e) => onChange(e.target.value)}
      />
      {error ? <p className="account-error">{error}</p> : hint && <p className="account-hint">{hint}</p>}
    </div>
  )
}

export default PasswordSettingsPage
