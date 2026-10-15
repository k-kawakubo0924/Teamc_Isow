import { useState } from 'react'
import { Navigate, useNavigate } from 'react-router'
import { sendAppeal } from '../api/contacts'
import { useAuth } from '../auth/authContext'
import { ContactForm } from '../components/contact/ContactForm'
import './SuspendedPage.css'

/**
 * 利用停止の画面（docs/admin.md「6. 措置」）。API が利用停止を返したら、どの画面からでもここへ移る（RouteGuards）。
 * 理由と期限を出す。期限を過ぎれば、もう一度ログインすれば使える。
 * 「利用停止について問い合わせる」から申し立てられる（docs/settings.md「お問い合わせ」）。利用停止中はログインのトークンが
 * 出ないため、利用停止の応答で受け取った申し立て用の鍵（メモリにだけ持つ）で送る。
 * 利用停止の情報がない（再読み込みした・直接開いた）ときは、ログイン画面へ移る
 */
function SuspendedPage() {
  const { suspension, clearSuspension } = useAuth()
  const navigate = useNavigate()
  const [appealing, setAppealing] = useState(false)

  if (!suspension) return <Navigate to="/login" replace />

  const handleBack = () => {
    clearSuspension?.()
    navigate('/login', { replace: true })
  }

  const appealToken = suspension.appealToken

  return (
    <main className="suspended-page">
      <h1 className="suspended-title">アカウントが利用停止になっています</h1>
      <p className="suspended-text">運営の判断により、このアカウントは現在ご利用いただけません。</p>
      <dl className="suspended-details">
        <div className="suspended-row">
          <dt>理由</dt>
          <dd>{suspension.reasonLabel ?? '—'}</dd>
        </div>
        <div className="suspended-row">
          <dt>期間</dt>
          <dd>{suspension.suspendedUntil === null ? '無期限' : `${formatDateTime(suspension.suspendedUntil)} まで`}</dd>
        </div>
      </dl>
      {suspension.suspendedUntil !== null && (
        <p className="suspended-note">期間が過ぎると、もう一度ログインすれば使えるようになります。</p>
      )}

      {appealing ? (
        <section className="suspended-appeal" aria-label="利用停止についてのお問い合わせ">
          {appealToken === null ? (
            <p className="suspended-note" role="alert">
              受付の期限が切れました。もう一度ログインしてから送ってください。
            </p>
          ) : (
            <ContactForm
              initialCategory="ACCOUNT"
              emailHint="連絡先を残したい場合に入力してください。"
              notes={['現在、個別の返信は行っていません。', '見直しの結果は、ログインできるかどうかでお知らせします。']}
              onSubmit={async (request) => {
                await sendAppeal({ ...request, appealToken })
              }}
            />
          )}
        </section>
      ) : (
        <button type="button" className="suspended-contact" onClick={() => setAppealing(true)}>
          利用停止について問い合わせる
        </button>
      )}

      <button type="button" className="suspended-back" onClick={handleBack}>
        ログイン画面に戻る
      </button>
    </main>
  )
}

/** "2026-10-22T10:00:00" を「2026/10/22 10:00」にする。サーバーの日時をそのまま出す（ブラウザのタイムゾーンで変換しない） */
function formatDateTime(value: string): string {
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})/)
  return m === null ? value : `${m[1]}/${m[2]}/${m[3]} ${m[4]}:${m[5]}`
}

export default SuspendedPage
