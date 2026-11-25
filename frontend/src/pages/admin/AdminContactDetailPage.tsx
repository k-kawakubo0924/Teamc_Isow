import { useEffect, useId, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  changeContactStatus,
  fetchContact,
  fetchModerationUser,
  type AdminContactDetail,
  type AdminContactStatus,
  type ModerationUserState,
} from '../../api/admin'
import { ApiError } from '../../api/client'
import { contactCategoryLabel } from '../../api/contacts'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'
import { CONTACT_CHANNEL_LABELS, CONTACT_STATUS_LABELS } from './contactLabels'
import { UserModerationPanel } from './ModerationPanels'

/** メモの文字数の上限（バックエンドの ContactInquiry.MAX_MEMO_LENGTH） */
const MAX_MEMO_LENGTH = 1000

/**
 * お問い合わせの詳細（/admin/contacts/{id}。docs/admin.md「7. お問い合わせ」）。
 * 内容は HTML として扱わず、改行はそのまま出す。状態（未対応・対応済み・対応不要）とメモを記録する。
 * 利用停止中の申し立てでは、その人の利用停止の状態と解除（措置の画面と同じ部品）を出す。
 * 開いたことは操作ログに残らない（本人が運営に宛てて送ったもので、読むこと自体が目的のため）
 */
function AdminContactDetailPage() {
  const { contactId } = useParams()
  const { token } = useAuth()
  const id = contactId !== undefined && /^\d+$/.test(contactId) ? Number(contactId) : null
  const [detail, setDetail] = useState<AdminContactDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    if (id === null || !token) return
    let cancelled = false
    fetchContact(id, token)
      .then((res) => {
        if (!cancelled) setDetail(res)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '読み込めませんでした。')
      })
    return () => {
      cancelled = true
    }
  }, [id, token])

  return (
    <>
      <p className="admin-report-back">
        <Link to="/admin/contacts">‹ お問い合わせの一覧へ</Link>
      </p>
      <h1 className="admin-page-title">お問い合わせの詳細</h1>
      <p className="admin-report-notice" role="note">
        返信の手段はまだありません（送った人には「個別の返信は行っていません」と伝えています）。
        利用停止の見直しは、下の「利用停止」から解除すると、その人がログインできるようになることで伝わります。
      </p>
      {id === null && <p className="admin-reports-error">お問い合わせが見つかりません。</p>}
      {error && (
        <p className="admin-reports-error" role="alert">
          {error}
        </p>
      )}
      {detail === null
        ? !error && id !== null && <p className="admin-reports-empty">読み込み中…</p>
        : token !== null && <ContactDetail detail={detail} token={token} onChange={setDetail} />}
    </>
  )
}

function ContactDetail({
  detail,
  token,
  onChange,
}: {
  detail: AdminContactDetail
  token: string
  onChange: (detail: AdminContactDetail) => void
}) {
  return (
    <div className="admin-report">
      <section className="admin-report-section">
        <p className="admin-report-status">
          状態：<strong>{CONTACT_STATUS_LABELS[detail.status] ?? detail.status}</strong>
          {detail.handledByUsername && (
            <span>
              （{detail.handledByUsername}
              {detail.closedAt && ` ・ ${formatAdminDateTime(detail.closedAt)}`}）
            </span>
          )}
        </p>
        <p className="admin-report-meta">
          {CONTACT_CHANNEL_LABELS[detail.channel] ?? detail.channel} ・ {contactCategoryLabel(detail.category)} ・ 受付{' '}
          {formatAdminDateTime(detail.createdAt)}
        </p>
        <p className="admin-report-meta">
          送った人：{detail.username === null ? '（退会したユーザー）' : `@${detail.username}`}（ユーザー ID {detail.userId}）
        </p>
        <p className="admin-report-meta">
          別の連絡先：{detail.email ?? '入力なし（登録済みのアドレスと同じ、または未入力）'}
        </p>
      </section>

      <section className="admin-report-section">
        <h2 className="admin-report-heading">内容</h2>
        <p className="admin-report-body">{detail.body}</p>
      </section>

      {detail.channel === 'SUSPENDED_APPEAL' && <SuspensionSection userId={detail.userId} token={token} />}
      <StatusForm detail={detail} token={token} onChange={onChange} />
    </div>
  )
}

/** 申し立てた人の利用停止の状態と解除（docs/admin.md「6. 措置」の部品をそのまま使う） */
function SuspensionSection({ userId, token }: { userId: number; token: string }) {
  const [user, setUser] = useState<ModerationUserState | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    fetchModerationUser(userId, token)
      .then((res) => {
        if (!cancelled) setUser(res)
      })
      .catch((err: unknown) => {
        if (cancelled) return
        setError(
          err instanceof ApiError && err.status === 404
            ? 'ユーザーが見つかりません（退会した可能性があります）。'
            : err instanceof Error
              ? err.message
              : '読み込めませんでした。',
        )
      })
    return () => {
      cancelled = true
    }
  }, [userId, token])

  return (
    <section className="admin-report-section admin-report-moderation">
      <h2 className="admin-report-heading">利用停止</h2>
      {error && <p className="admin-reports-error">{error}</p>}
      {user && <UserModerationPanel state={user} onChange={setUser} />}
    </section>
  )
}

/** 状態の記録。メモは管理者にだけ見せ、操作ログには書かない */
function StatusForm({
  detail,
  token,
  onChange,
}: {
  detail: AdminContactDetail
  token: string
  onChange: (detail: AdminContactDetail) => void
}) {
  const memoId = useId()
  const [memo, setMemo] = useState(detail.adminMemo ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const tooLong = memo.trim().length > MAX_MEMO_LENGTH

  const submit = async (status: AdminContactStatus) => {
    if (busy || tooLong) return
    setBusy(true)
    setError(null)
    try {
      const result = await changeContactStatus(detail.id, status, memo.trim(), token)
      onChange({
        ...detail,
        status: result.status,
        closedAt: result.closedAt,
        handledByUsername: result.handledByUsername,
        adminMemo: result.adminMemo,
      })
    } catch (err) {
      setError(err instanceof Error ? err.message : '記録できませんでした。')
    } finally {
      setBusy(false)
    }
  }

  return (
    <section className="admin-report-section">
      <h2 className="admin-report-heading">状態の記録</h2>
      <label htmlFor={memoId} className="admin-report-meta">
        メモ（任意。管理者だけが見ます。操作ログには書きません）
      </label>
      <textarea
        id={memoId}
        className="admin-report-memo"
        rows={3}
        value={memo}
        aria-invalid={tooLong}
        onChange={(e) => setMemo(e.target.value)}
      />
      <p className={`admin-report-meta${tooLong ? ' admin-report-over' : ''}`}>
        {memo.trim().length} / {MAX_MEMO_LENGTH}
      </p>
      {error && (
        <p className="admin-reports-error" role="alert">
          {error}
        </p>
      )}
      <div className="admin-report-buttons">
        {detail.status === 'OPEN' ? (
          <>
            <button type="button" className="admin-report-button" disabled={busy || tooLong} onClick={() => submit('RESOLVED')}>
              対応済みにする
            </button>
            <button type="button" className="admin-report-button" disabled={busy || tooLong} onClick={() => submit('DISMISSED')}>
              対応不要にする
            </button>
          </>
        ) : (
          <button type="button" className="admin-report-button" disabled={busy || tooLong} onClick={() => submit('OPEN')}>
            未対応に戻す
          </button>
        )}
      </div>
    </section>
  )
}

export default AdminContactDetailPage
