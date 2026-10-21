import { useEffect, useId, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  changeReportStatus,
  evidenceImagePath,
  fetchEvidenceImage,
  fetchModerationPost,
  fetchModerationUser,
  fetchReport,
  type AdminReportDetail,
  type AdminReportStatus,
  type ModerationPostState,
  type ModerationUserState,
} from '../../api/admin'
import { ApiError } from '../../api/client'
import { reportReasonLabel } from '../../api/reports'
import { useAuth } from '../../auth/authContext'
import { useAuthorizedImage } from '../../hooks/useAuthorizedImage'
import { formatAdminDateTime } from './adminFormat'
import { PostModerationPanel, UserModerationPanel } from './ModerationPanels'
import { REPORT_CURRENT_STATE_LABELS, REPORT_STATUS_LABELS, REPORT_TARGET_LABELS } from './reportLabels'

/** メモの文字数の上限（バックエンドの ReportCase.MAX_MEMO_LENGTH） */
const MAX_MEMO_LENGTH = 1000

/**
 * 通報の詳細（/admin/reports/{id}。docs/admin.md「通報」）。
 * 通報した時点の内容（証拠）・対象の今の状態・通報した人と理由を出し、状態（対応済み・却下・未対応）を記録する。
 * 「対応済み」にするだけでは措置は行われないため、そのことを画面の上に明記する。
 * 措置（投稿の非公開化・利用停止。docs/admin.md「6. 措置」）は下の「措置」から行い、措置にこの通報を結びつける
 */
function AdminReportDetailPage() {
  const { caseId } = useParams()
  const { token } = useAuth()
  const id = caseId !== undefined && /^\d+$/.test(caseId) ? Number(caseId) : null
  const [detail, setDetail] = useState<AdminReportDetail | null>(null)
  const [error, setError] = useState<string | null>(null)
  /** 措置とあわせて通報を対応済みにしたら増やし、詳細を読み直す */
  const [version, setVersion] = useState(0)

  useEffect(() => {
    if (id === null || !token) return
    let cancelled = false
    fetchReport(id, token)
      .then((res) => {
        if (!cancelled) setDetail(res)
      })
      .catch((err: unknown) => {
        if (!cancelled) setError(err instanceof Error ? err.message : '読み込めませんでした。')
      })
    return () => {
      cancelled = true
    }
  }, [id, token, version])

  return (
    <>
      <p className="admin-report-back">
        <Link to="/admin/reports">‹ 通報の一覧へ</Link>
      </p>
      <h1 className="admin-page-title">通報の詳細</h1>
      <p className="admin-report-notice" role="note">
        「対応済み」にしても、投稿やユーザーには何も起きません。「状態の記録」は、確かめた結果の状態を記録するだけです。
        投稿の非公開化・ユーザーの利用停止は、下の「措置」から行ってください（投稿の削除はまだできません）。
      </p>
      {id === null && <p className="admin-reports-error">通報が見つかりません。</p>}
      {error && (
        <p className="admin-reports-error" role="alert">
          {error}
        </p>
      )}
      {detail === null ? (
        !error && id !== null && <p className="admin-reports-empty">読み込み中…</p>
      ) : (
        token !== null && (
          <ReportDetail
            detail={detail}
            token={token}
            onChange={setDetail}
            onReload={() => setVersion((v) => v + 1)}
          />
        )
      )}
    </>
  )
}

function ReportDetail({
  detail,
  token,
  onChange,
  onReload,
}: {
  detail: AdminReportDetail
  token: string
  onChange: (detail: AdminReportDetail) => void
  onReload: () => void
}) {
  return (
    <div className="admin-report">
      <section className="admin-report-section">
        <p className="admin-report-status">
          状態：<strong>{REPORT_STATUS_LABELS[detail.status] ?? detail.status}</strong>
          {detail.handledByUsername && (
            <span>
              （{detail.handledByUsername}
              {detail.closedAt && ` ・ ${formatAdminDateTime(detail.closedAt)}`}）
            </span>
          )}
        </p>
        <p className="admin-report-meta">
          {REPORT_TARGET_LABELS[detail.targetType] ?? detail.targetType}（ID {detail.targetId}）・ 通報 {detail.reportCount}件 ・
          最初の通報 {formatAdminDateTime(detail.firstReportedAt)}
        </p>
        <p className="admin-report-meta">今の状態：{REPORT_CURRENT_STATE_LABELS[detail.currentState] ?? detail.currentState}</p>
      </section>

      <section className="admin-report-section">
        <h2 className="admin-report-heading">通報した時点の内容（証拠）</h2>
        {detail.evidencePurged || detail.evidence === null ? (
          <p className="admin-report-meta">
            保存期間（対応を終えてから180日）を過ぎたため、証拠は削除しました
            {detail.evidencePurgedAt && `（${formatAdminDateTime(detail.evidencePurgedAt)}）`}。
          </p>
        ) : (
          <Evidence detail={detail} token={token} />
        )}
      </section>

      <section className="admin-report-section">
        <h2 className="admin-report-heading">通報した人と理由</h2>
        <ul className="admin-report-reports">
          {detail.reports.map((report) => (
            <li key={`${report.reporterId}-${report.reportedAt}`} className="admin-report-report">
              <p className="admin-report-meta">
                {report.reporterUsername} ・ {reportReasonLabel(report.reason)} ・ {formatAdminDateTime(report.reportedAt)}
              </p>
              {report.comment && <p className="admin-report-body">{report.comment}</p>}
            </li>
          ))}
        </ul>
      </section>

      <ModerationSection detail={detail} token={token} onReload={onReload} />
      <StatusForm detail={detail} token={token} onChange={onChange} />
    </div>
  )
}

/** 証拠（対象の種類ごと）。文字は HTML として扱わず、改行はそのまま出す */
function Evidence({ detail, token }: { detail: AdminReportDetail; token: string }) {
  const evidence = detail.evidence
  if (!evidence) return null
  const filesWithoutMessage = detail.evidenceFiles.filter((file) => file.messageId === null)

  if (evidence.post) {
    const post = evidence.post
    return (
      <div className="admin-report-evidence">
        <h3 className="admin-report-title">{post.title}</h3>
        <p className="admin-report-meta">
          投稿者 @{post.authorUsername} ・ {post.fashionCategory} ・ タグ：{post.tags.join('、')} ・ 投稿{' '}
          {formatAdminDateTime(post.createdAt)}
        </p>
        <EvidenceImages caseId={detail.caseId} fileIds={filesWithoutMessage.map((file) => file.id)} token={token} />
        {post.wornItems && <p className="admin-report-body">{post.wornItems}</p>}
        <p className="admin-report-body">{post.description}</p>
        {post.referenceUrl && <p className="admin-report-meta">参考情報：{post.referenceUrl}</p>}
      </div>
    )
  }
  if (evidence.user) {
    return (
      <div className="admin-report-evidence">
        <p className="admin-report-title">
          @{evidence.user.username}（{evidence.user.displayName}）
        </p>
        <EvidenceImages caseId={detail.caseId} fileIds={filesWithoutMessage.map((file) => file.id)} token={token} />
      </div>
    )
  }
  if (evidence.messages) {
    return (
      <div className="admin-report-evidence">
        <p className="admin-report-meta">
          通報されたメッセージと、その前後5件ずつだけを表示します（それより前後は管理者も見られません）。
          DM の内容を表示したことは、操作ログに記録されます。
        </p>
        <ol className="admin-report-messages">
          {evidence.messages.map((message) => {
            const files = detail.evidenceFiles.filter((file) => file.messageId === message.messageId)
            return (
              <li
                key={message.messageId}
                className={`admin-report-message${message.reported ? ' admin-report-message-reported' : ''}`}
                aria-current={message.reported ? 'true' : undefined}
              >
                {message.reported && <p className="admin-report-message-label">通報されたメッセージ</p>}
                <p className="admin-report-meta">
                  @{message.senderUsername} ・ {formatAdminDateTime(message.sentAt)}
                </p>
                {message.body !== null && <p className="admin-report-body">{message.body}</p>}
                {files.length > 0 && (
                  <EvidenceImages caseId={detail.caseId} fileIds={files.map((file) => file.id)} token={token} />
                )}
              </li>
            )
          })}
        </ol>
      </div>
    )
  }
  return null
}

/**
 * 証拠の写真。管理者向けの API から認証つきで取得し、一時的な URL にして表示する（/uploads/** の URL は使わない）。
 * 画面を離れたら一時的な URL を解放する
 */
function EvidenceImages({ caseId, fileIds, token }: { caseId: number; fileIds: number[]; token: string }) {
  if (fileIds.length === 0) return null
  return (
    <div className="admin-report-images">
      {fileIds.map((fileId) => (
        <EvidenceImage key={fileId} caseId={caseId} fileId={fileId} token={token} />
      ))}
    </div>
  )
}

/** 証拠の写真。管理者向けの API から認証付きで取得し、その場限りの URL にして表示する（DM の画像と同じ部品。docs/dm.md「画像」） */
function EvidenceImage({ caseId, fileId, token }: { caseId: number; fileId: number; token: string }) {
  const { url, failed } = useAuthorizedImage(evidenceImagePath(caseId, fileId), token, (t) =>
    fetchEvidenceImage(caseId, fileId, t),
  )

  if (failed) return <p className="admin-report-meta">写真を読み込めませんでした。</p>
  if (!url) return <p className="admin-report-meta">写真を読み込み中…</p>
  return <img className="admin-report-evidence-image" src={url} alt="証拠の写真" />
}

/**
 * 措置（docs/admin.md「6. 措置」）。投稿の通報なら投稿の非公開化（投稿が残っている場合）と投稿者の利用停止、
 * ユーザー・DM の通報なら通報された人の利用停止を出す。措置にはこの通報を結びつけ、選べば同時に対応済みにする
 */
function ModerationSection({
  detail,
  token,
  onReload,
}: {
  detail: AdminReportDetail
  token: string
  onReload: () => void
}) {
  const withPost = detail.targetType === 'POST' && detail.currentState !== 'DELETED'
  const [post, setPost] = useState<ModerationPostState | null>(null)
  const [user, setUser] = useState<ModerationUserState | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    const fail = (err: unknown) => {
      if (cancelled) return
      setError(
        err instanceof ApiError && err.status === 404
          ? '対象が見つかりません（削除・退会した可能性があります）。'
          : err instanceof Error
            ? err.message
            : '読み込めませんでした。',
      )
    }
    if (withPost) {
      fetchModerationPost(detail.targetId, token)
        .then((res) => {
          if (!cancelled) setPost(res)
        })
        .catch(fail)
    }
    fetchModerationUser(detail.targetUserId, token)
      .then((res) => {
        if (!cancelled) setUser(res)
      })
      .catch(fail)
    return () => {
      cancelled = true
    }
  }, [withPost, detail.targetId, detail.targetUserId, token])

  // 対応を終えた通報にも措置を結びつけられるが、「対応済みにする」を選べるのは未対応のときだけ
  const canResolve = detail.status === 'OPEN'

  return (
    <section className="admin-report-section admin-report-moderation">
      <h2 className="admin-report-heading">措置</h2>
      {error && <p className="admin-reports-error">{error}</p>}
      {post && (
        <PostModerationPanel
          state={post}
          reportCaseId={detail.caseId}
          canResolve={canResolve}
          onChange={(next) => {
            setPost(next)
            // 措置とあわせて通報を対応済みにしたかもしれないため、詳細を読み直す
            onReload()
          }}
        />
      )}
      {user && (
        <UserModerationPanel
          state={user}
          reportCaseId={detail.caseId}
          canResolve={canResolve}
          onChange={(next) => {
            setUser(next)
            onReload()
          }}
        />
      )}
    </section>
  )
}

/** 状態の記録（措置は行わない）。メモは管理者にだけ見せ、操作ログには書かない */
function StatusForm({
  detail,
  token,
  onChange,
}: {
  detail: AdminReportDetail
  token: string
  onChange: (detail: AdminReportDetail) => void
}) {
  const memoId = useId()
  const [memo, setMemo] = useState(detail.adminMemo ?? '')
  const [busy, setBusy] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const tooLong = memo.trim().length > MAX_MEMO_LENGTH

  const submit = async (status: AdminReportStatus) => {
    if (busy || tooLong) return
    setBusy(true)
    setError(null)
    try {
      const result = await changeReportStatus(detail.caseId, status, memo.trim(), token)
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
              却下する
            </button>
          </>
        ) : (
          <button
            type="button"
            className="admin-report-button"
            disabled={busy || tooLong || detail.evidencePurged}
            onClick={() => submit('OPEN')}
          >
            未対応に戻す
          </button>
        )}
      </div>
    </section>
  )
}

export default AdminReportDetailPage
