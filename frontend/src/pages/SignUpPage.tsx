import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router'
import { register } from '../api/auth'
import { ApiError } from '../api/client'
import {
  formatPhoneNumber,
  INVALID_INPUT_MESSAGE,
  normalizeSignUpForm,
  normalizeUsername,
  validateSignUp,
  type FieldErrors,
  type SignUpField,
  type SignUpForm,
} from '../validation/authRules'
import './auth.css'

const EMPTY_FORM: SignUpForm = { email: '', phoneNumber: '', password: '', username: '' }

const FIELDS: { name: SignUpField; label: string; type: string; placeholder: string; autoComplete: string }[] = [
  { name: 'email', label: 'メールアドレス', type: 'email', placeholder: 'example@mail.com', autoComplete: 'email' },
  { name: 'phoneNumber', label: '電話番号', type: 'tel', placeholder: '09012345678', autoComplete: 'tel' },
  { name: 'password', label: 'パスワード', type: 'password', placeholder: '8文字以上、英字と数字を含む', autoComplete: 'new-password' },
  { name: 'username', label: 'ユーザー名', type: 'text', placeholder: '@yuu_style', autoComplete: 'username' },
]

/**
 * 新規会員登録（docs/auth.md）。
 * 入力画面と確認画面を同じページ内で切り替える（「内容を変更」で戻ったときに入力値を残すため）。
 */
function SignUpPage() {
  const navigate = useNavigate()
  const [form, setForm] = useState<SignUpForm>(EMPTY_FORM)
  const [step, setStep] = useState<'input' | 'confirm'>('input')
  const [fieldErrors, setFieldErrors] = useState<FieldErrors>({})
  const [bannerMessage, setBannerMessage] = useState<string | null>(null)
  const [submitting, setSubmitting] = useState(false)

  const handleChange = (name: SignUpField, value: string) => {
    setForm((prev) => ({ ...prev, [name]: value }))
    // 書き直した欄の警告文は消す（他の欄の警告文と上部の警告文は、次に確認するまで残す）
    setFieldErrors((prev) => {
      if (!(name in prev)) return prev
      const { [name]: _removed, ...rest } = prev
      return rest
    })
  }

  const handleConfirm = (e: FormEvent<HTMLFormElement>) => {
    e.preventDefault()
    const errors = validateSignUp(form)
    setFieldErrors(errors)
    if (Object.keys(errors).length > 0) {
      setBannerMessage(INVALID_INPUT_MESSAGE)
      return
    }
    setBannerMessage(null)
    setStep('confirm')
  }

  const handleBack = () => {
    setBannerMessage(null)
    setStep('input')
  }

  const handleRegister = async () => {
    setSubmitting(true)
    setBannerMessage(null)
    try {
      await register(normalizeSignUpForm(form))
      navigate('/login', { state: { registered: true } })
    } catch (err) {
      if (err instanceof ApiError && (err.status === 409 || err.status === 400) && err.body) {
        // 重複・形式エラーは入力画面に戻し、該当する欄に警告文を出す
        setFieldErrors(err.body.errors as FieldErrors)
        setBannerMessage(err.body.message)
        setStep('input')
      } else {
        setBannerMessage(err instanceof Error ? err.message : String(err))
      }
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <main className="auth-page">
      <h1 className="auth-logo">ISHO</h1>
      <p className="auth-subtitle">{step === 'input' ? '新規登録' : '入力内容の確認'}</p>

      {bannerMessage && <ErrorBanner message={bannerMessage} />}

      {step === 'input' ? (
        <form className="auth-form" onSubmit={handleConfirm} noValidate>
          {FIELDS.map((field) => {
            const error = fieldErrors[field.name]
            const inputId = `signup-${field.name}`
            return (
              <div className="auth-field" key={field.name}>
                <label className="auth-label" htmlFor={inputId}>
                  {field.label}
                </label>
                <input
                  id={inputId}
                  className={`auth-input${error ? ' auth-input-error' : ''}`}
                  type={field.type}
                  placeholder={field.placeholder}
                  autoComplete={field.autoComplete}
                  value={form[field.name]}
                  onChange={(e) => handleChange(field.name, e.target.value)}
                  aria-invalid={error ? true : undefined}
                  aria-describedby={error ? `${inputId}-error` : undefined}
                />
                {error && (
                  <p className="auth-field-error" id={`${inputId}-error`}>
                    {error}
                  </p>
                )}
              </div>
            )
          })}

          <div className="auth-actions">
            <button type="submit" className="auth-button auth-button-primary">
              入力情報を確認
            </button>
          </div>

          <p className="auth-footer">
            すでにアカウントをお持ちの方
            <Link to="/login" className="auth-link">
              ログイン
            </Link>
          </p>
        </form>
      ) : (
        <div className="auth-form">
          <dl className="auth-confirm-list">
            <div className="auth-confirm-row">
              <dt>メールアドレス</dt>
              <dd>{normalizeSignUpForm(form).email}</dd>
            </div>
            <div className="auth-confirm-row">
              <dt>電話番号</dt>
              <dd>{formatPhoneNumber(form.phoneNumber)}</dd>
            </div>
            <div className="auth-confirm-row">
              <dt>パスワード</dt>
              <dd aria-label="パスワード（非表示）">{'•'.repeat(form.password.length)}</dd>
            </div>
            <div className="auth-confirm-row">
              <dt>ユーザー名</dt>
              <dd>@{normalizeUsername(form.username)}</dd>
            </div>
          </dl>

          <p className="auth-note">
            登録すると、利用規約およびプライバシーポリシーに
            <br />
            同意したものとみなされます
          </p>

          <div className="auth-actions auth-actions-split">
            <button type="button" className="auth-button auth-button-secondary" onClick={handleBack} disabled={submitting}>
              内容を変更
            </button>
            <button type="button" className="auth-button auth-button-primary" onClick={handleRegister} disabled={submitting}>
              {submitting ? '登録中...' : '新規登録'}
            </button>
          </div>
        </div>
      )}
    </main>
  )
}

function ErrorBanner({ message }: { message: string }) {
  return (
    <div className="auth-banner" role="alert">
      <span className="auth-banner-icon" aria-hidden="true">
        !
      </span>
      <p>{message}</p>
    </div>
  )
}

export default SignUpPage
