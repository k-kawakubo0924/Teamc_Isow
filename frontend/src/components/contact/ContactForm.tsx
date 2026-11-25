import { useEffect, useId, useRef, useState, type FormEvent } from 'react'
import { ApiError } from '../../api/client'
import {
  CONTACT_CATEGORIES,
  MAX_CONTACT_BODY_LENGTH,
  type ContactCategory,
  type ContactRequest,
} from '../../api/contacts'
import './contact.css'

type FieldErrors = Partial<Record<'email' | 'category' | 'body', string>>

/**
 * お問い合わせのフォーム（docs/settings.md「お問い合わせ」、design/Contact us.png）。
 * ログイン中のお問い合わせ（ContactPage）と、利用停止の画面の申し立て（SuspendedPage）で使う。
 *
 * - 返信先メールアドレスは任意（設計画像では必須だが、返信しないのに必須で集めないため変えた。docs/settings.md）
 * - 種類と内容は必須。未記入なら、送らずに欄の下に警告を出す。内容は1000文字まで
 * - notes は、フォームの上に出す伝えごと（返信しないこと など）。送った後も出す
 */
export function ContactForm({
  initialEmail = '',
  initialCategory = '',
  emailHint,
  notes,
  onSubmit,
}: {
  /** 最初から入れておくアドレス（後から届いても、まだ書き換えていなければ入れる） */
  initialEmail?: string
  initialCategory?: ContactCategory | ''
  emailHint: string
  notes: string[]
  onSubmit: (request: ContactRequest) => Promise<void>
}) {
  const emailId = useId()
  const categoryId = useId()
  const bodyId = useId()
  const [email, setEmail] = useState(initialEmail)
  const emailEdited = useRef(false)
  const [category, setCategory] = useState<ContactCategory | ''>(initialCategory)
  const [body, setBody] = useState('')
  const [errors, setErrors] = useState<FieldErrors>({})
  const [message, setMessage] = useState<string | null>(null)
  const [busy, setBusy] = useState(false)
  const [done, setDone] = useState(false)

  useEffect(() => {
    if (!emailEdited.current) setEmail(initialEmail)
  }, [initialEmail])

  const length = body.trim().length
  const tooLong = length > MAX_CONTACT_BODY_LENGTH

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (busy) return
    const next: FieldErrors = {}
    if (category === '') next.category = 'お問い合わせの種類を選択してください'
    if (length === 0) next.body = 'お問い合わせ内容を入力してください'
    else if (tooLong) next.body = `お問い合わせ内容は${MAX_CONTACT_BODY_LENGTH}文字以内で入力してください`
    setErrors(next)
    setMessage(null)
    if (category === '' || Object.keys(next).length > 0) return

    setBusy(true)
    try {
      await onSubmit({ email: email.trim(), category, body: body.trim() })
      setDone(true)
    } catch (err) {
      // 欄の下に出せる誤りだけを欄に出す。ほかの誤り（上限・鍵の期限切れなど）はまとめて出す
      const serverErrors = err instanceof ApiError ? (err.body?.errors ?? {}) : {}
      const fieldErrors: FieldErrors = {}
      for (const field of ['email', 'category', 'body'] as const) {
        if (serverErrors[field]) fieldErrors[field] = serverErrors[field]
      }
      if (Object.keys(fieldErrors).length > 0) {
        setErrors(fieldErrors)
      } else {
        setMessage(err instanceof Error ? err.message : '送信できませんでした。時間をおいて再度お試しください。')
      }
    } finally {
      setBusy(false)
    }
  }

  const noteBox = (
    <div className="contact-notes" role="note">
      {notes.map((note) => (
        <p key={note}>{note}</p>
      ))}
    </div>
  )

  if (done) {
    return (
      <div className="contact-done">
        <p className="contact-done-title" role="status">
          お問い合わせを受け付けました。
        </p>
        {noteBox}
      </div>
    )
  }

  return (
    <form className="contact-form" noValidate onSubmit={handleSubmit}>
      {noteBox}

      <div className="contact-field">
        <label htmlFor={emailId} className="contact-label">
          返信先メールアドレス<span className="contact-optional">任意</span>
        </label>
        <input
          id={emailId}
          type="email"
          className="contact-input"
          placeholder="example@mail.com"
          autoComplete="email"
          value={email}
          aria-invalid={errors.email !== undefined}
          aria-describedby={`${emailId}-hint`}
          onChange={(e) => {
            emailEdited.current = true
            setEmail(e.target.value)
          }}
        />
        <p id={`${emailId}-hint`} className="contact-hint">
          {emailHint}
        </p>
        {errors.email && <p className="contact-error">{errors.email}</p>}
      </div>

      <div className="contact-field">
        <label htmlFor={categoryId} className="contact-label">
          お問い合わせの種類<span className="contact-required">必須</span>
        </label>
        <select
          id={categoryId}
          className="contact-input"
          value={category}
          aria-invalid={errors.category !== undefined}
          onChange={(e) => setCategory(e.target.value as ContactCategory | '')}
        >
          <option value="">選択してください</option>
          {CONTACT_CATEGORIES.map((option) => (
            <option key={option.category} value={option.category}>
              {option.label}
            </option>
          ))}
        </select>
        {errors.category && <p className="contact-error">{errors.category}</p>}
      </div>

      <div className="contact-field">
        <label htmlFor={bodyId} className="contact-label">
          お問い合わせ内容<span className="contact-required">必須</span>
        </label>
        <textarea
          id={bodyId}
          className="contact-input contact-textarea"
          placeholder="できるだけ具体的にご記入ください"
          rows={10}
          value={body}
          aria-invalid={errors.body !== undefined || tooLong}
          onChange={(e) => setBody(e.target.value)}
        />
        <p className={`contact-count${tooLong ? ' contact-count-over' : ''}`}>
          {length} / {MAX_CONTACT_BODY_LENGTH}
        </p>
        {errors.body && <p className="contact-error">{errors.body}</p>}
      </div>

      {message && (
        <p className="contact-error contact-message" role="alert">
          {message}
        </p>
      )}

      <div className="contact-actions">
        <button type="submit" className="contact-submit" disabled={busy}>
          送信
        </button>
      </div>
    </form>
  )
}
