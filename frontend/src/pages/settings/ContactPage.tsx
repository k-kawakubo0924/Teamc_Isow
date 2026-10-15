import { useEffect, useState } from 'react'
import { useLocation, useNavigate } from 'react-router'
import { me } from '../../api/auth'
import { sendContact } from '../../api/contacts'
import { useAuth } from '../../auth/authContext'
import { ContactForm } from '../../components/contact/ContactForm'
import { goBack } from './goBack'
import './settings.css'

/**
 * お問い合わせ（/settings/contact。docs/settings.md「お問い合わせ」、design/Contact us.png）。詳細設定から開く。
 * 返信先メールアドレスは任意で、登録済みのアドレスを最初から入れる（別の連絡先を使いたい場合に書き換える欄）。
 * 返信はしないため、フォームの上でそのことを伝える
 */
function ContactPage() {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const [registeredEmail, setRegisteredEmail] = useState('')

  useEffect(() => {
    if (!token) return
    let cancelled = false
    me(token)
      .then((user) => {
        if (!cancelled) setRegisteredEmail(user.email)
      })
      // 読み込めなくても送れる（アドレスは任意のため、空のままにする）
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [token])

  return (
    <main className="settings-page">
      <header className="settings-header">
        <button
          type="button"
          className="settings-back"
          aria-label="戻る"
          onClick={() => goBack(navigate, location, '/settings')}
        >
          ‹
        </button>
        <h1 className="settings-title">お問い合わせ</h1>
      </header>
      <div className="contact-page-body">
        <ContactForm
          initialEmail={registeredEmail}
          emailHint="登録済みのアドレスが入っています。別の連絡先を使いたい場合に書き換えてください。"
          notes={['現在、個別の返信は行っていません。']}
          onSubmit={async (request) => {
            // RequireAuth の中なのでトークンはある。ない場合は、送れたように見せない
            if (!token) throw new Error('ログインし直してから送ってください。')
            await sendContact(request, token)
          }}
        />
      </div>
    </main>
  )
}

export default ContactPage
