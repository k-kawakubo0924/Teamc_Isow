import { Navigate, useNavigate } from 'react-router'
import { useAuth } from '../auth/authContext'
import './SuspendedPage.css'

/**
 * 利用停止の画面（docs/admin.md「6. 措置」）。API が利用停止を返したら、どの画面からでもここへ移る（RouteGuards）。
 * 理由と期限を出す。期限を過ぎれば、もう一度ログインすれば使える。
 * お問い合わせの窓口はまだないため「お問い合わせ（準備中）」とだけ出す。
 * 利用停止の情報がない（再読み込みした・直接開いた）ときは、ログイン画面へ移る
 */
function SuspendedPage() {
  const { suspension, clearSuspension } = useAuth()
  const navigate = useNavigate()

  if (!suspension) return <Navigate to="/login" replace />

  const handleBack = () => {
    clearSuspension?.()
    navigate('/login', { replace: true })
  }

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
      <p className="suspended-contact">お問い合わせ（準備中）</p>
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
