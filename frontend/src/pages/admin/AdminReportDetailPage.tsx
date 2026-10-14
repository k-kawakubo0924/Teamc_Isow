import { useEffect, useId, useState } from 'react'
import { Link, useParams } from 'react-router'
import {
  changeReportStatus,
  fetchEvidenceImage,
  fetchReport,
  type AdminReportDetail,
  type AdminReportStatus,
} from '../../api/admin'
import { reportReasonLabel } from '../../api/reports'
import { useAuth } from '../../auth/authContext'
import { formatAdminDateTime } from './adminFormat'
import { REPORT_CURRENT_STATE_LABELS, REPORT_STATUS_LABELS, REPORT_TARGET_LABELS } from './reportLabels'

/** メモの文字数の上限（バックエンドの ReportCase.MAX_MEMO_LENGTH） */
const MAX_MEMO_LENGTH = 1000

/**
 * 通報の詳細（/admin/reports/{id}。docs/admin.md「通報」）。
 * 通報した時点の内容（証拠）・対象の今の状態・通報した人と理由を出し、状態（対応済み・却下・未対応）を記録する。
 * **措置（非公開化・削除・利用停止）の機能はまだない**ため、そのことを画面の上に明記する（「対応済み」にしても何も起きない）
 */
function AdminReportDetailPage() {
  const { caseId } = useParams()
  const { token } = useAuth()
  const id = caseId !== undefined && /^\d+$/.test(caseId) ? Number(caseId) : null
  const [detail, setDetail] = useState<AdminReportDetail | null>(null)
  const [error, setError] = useState<string | null>(null)

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
  }, [id, token])

  return (
    <>
      <p className="admin-report-back">
        <Link to="/admin/reports">‹ 通報の一覧へ</Link>
      </p>
      <h1 className="admin-page-title">通報の詳細</h1>
      <p className="admin-report-notice" role="note">
        措置（投稿の非公開化・削除、ユーザーの利用停止）の機能はまだありません。「対応済み」にしても、投稿やユーザーには何も起きません。
        この画面では、確かめた結果の状態を記録するだけです。措置が必要な場合は、別途対応してください。
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
        token !== null && <ReportDetail detail={detail} token={token} onChange={setDetail} />
      )}
    </>
  )
}

function ReportDetail({
  detail,
  token,
  onChange,
}: {
  detail: AdminReportDetail
  token: string
  onChange: (detail: AdminReportDetail) => void
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

function EvidenceImage({ caseId, fileId, token }: { caseId: number; fileId: number; token: string }) {
  const [url, setUrl] = useState<string | null>(null)
  const [failed, setFailed] = useState(false)

  useEffect(() => {
    let cancelled = false
    let objectUrl: string | null = null
    fetchEvidenceImage(caseId, fileId, token)
      .then((blob) => {
        if (cancelled) return
        objectUrl = URL.createObjectURL(blob)
        setUrl(objectUrl)
      })
      .catch(() => {
        if (!cancelled) setFailed(true)
      })
    return () => {
      cancelled = true
      if (objectUrl) URL.revokeObjectURL(objectUrl)
    }
  }, [caseId, fileId, token])

  if (failed) return <p className="admin-report-meta">写真を読み込めませんでした。</p>
  if (!url) return <p className="admin-report-meta">写真を読み込み中…</p>
  return <img className="admin-report-evidence-image" src={url} alt="証拠の写真" />
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
