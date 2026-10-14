import { useEffect, useId, useState } from 'react'
import { blockUser } from '../../api/blocks'
import {
  MAX_REPORT_COMMENT_LENGTH,
  REPORT_REASONS,
  reportTarget,
  type ReportReason,
  type ReportTargetType,
} from '../../api/reports'
import { useAuth } from '../../auth/authContext'
import { BlockConfirmDialog } from '../block/BlockConfirmDialog'
import { blockConfirmText } from '../block/blockText'
import './report.css'

const TITLES: Record<ReportTargetType, string> = {
  POST: '投稿を通報',
  USER: 'ユーザーを通報',
  MESSAGE: 'メッセージを通報',
}

/**
 * 通報の入力（docs/admin.md「通報」）。投稿の「…」・プロフィールの⋮・チャット画面のメッセージから開く。
 *
 * <ul>
 *   <li>理由は対象に合ったものだけを出す。「その他」は詳しい内容が必須。詳しい内容は500文字まで</li>
 *   <li>送ったら、通報したことは相手に伝わらないことを書く。通報と別に、ブロックもできることを案内する（自動ではブロックしない）</li>
 * </ul>
 *
 * @param owner 通報する対象の持ち主（投稿者・ユーザー本人・メッセージを送った人）。ブロックの案内に使う
 * @param blockable ブロックの案内を出すか（すでにブロックしている相手には出さない）
 */
export function ReportDialog({
  targetType,
  targetId,
  owner,
  blockable,
  onClose,
  onBlocked,
}: {
  targetType: ReportTargetType
  targetId: number
  owner: { id: number; username: string }
  blockable: boolean
  onClose: () => void
  onBlocked?: () => void
}) {
  const { token } = useAuth()
  const titleId = useId()
  const commentId = useId()
  const [reason, setReason] = useState<ReportReason | null>(null)
  const [comment, setComment] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [result, setResult] = useState<'ACCEPTED' | 'ALREADY_REPORTED' | null>(null)
  const [confirmingBlock, setConfirmingBlock] = useState(false)
  const [blocking, setBlocking] = useState(false)
  const [blockError, setBlockError] = useState<string | null>(null)

  // Esc で閉じる（送信中は閉じない）
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !sending && !confirmingBlock) onClose()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [sending, confirmingBlock, onClose])

  const options = REPORT_REASONS.filter((option) => option.targets.includes(targetType))
  const trimmed = comment.trim()
  const tooLong = trimmed.length > MAX_REPORT_COMMENT_LENGTH
  const canSend = reason !== null && !(reason === 'OTHER' && trimmed === '') && !tooLong && !sending

  const handleSend = async () => {
    if (!token || !canSend || reason === null) return
    setSending(true)
    setError(null)
    try {
      const response = await reportTarget({ targetType, targetId, reason, comment: trimmed }, token)
      setResult(response.status)
    } catch (err) {
      setError(err instanceof Error ? err.message : '通報できませんでした。時間をおいて再度お試しください。')
    } finally {
      setSending(false)
    }
  }

  const handleBlock = async () => {
    if (!token || blocking) return
    setBlocking(true)
    setBlockError(null)
    try {
      await blockUser(owner.id, token)
      setConfirmingBlock(false)
      onBlocked?.()
      onClose()
    } catch (err) {
      setBlockError(err instanceof Error ? err.message : 'ブロックできませんでした。時間をおいて再度お試しください。')
    } finally {
      setBlocking(false)
    }
  }

  const blockText = blockConfirmText(owner.username)

  return (
    <div className="report-backdrop">
      <div className="report-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className="report-title">
          {TITLES[targetType]}
        </h2>

        {result === null ? (
          <>
            <fieldset className="report-reasons">
              <legend className="report-legend">理由</legend>
              {options.map((option) => (
                <label key={option.reason} className="report-reason">
                  <input
                    type="radio"
                    name="report-reason"
                    value={option.reason}
                    checked={reason === option.reason}
                    onChange={() => setReason(option.reason)}
                  />
                  {option.label}
                </label>
              ))}
            </fieldset>

            <label htmlFor={commentId} className="report-legend">
              詳しい内容（{reason === 'OTHER' ? '必須' : '任意'}）
            </label>
            <textarea
              id={commentId}
              className={`report-comment${tooLong ? ' report-comment-error' : ''}`}
              rows={4}
              value={comment}
              placeholder="運営が確認するときの参考にします"
              onChange={(e) => setComment(e.target.value)}
            />
            <p className={`report-count${tooLong ? ' report-count-over' : ''}`}>
              {trimmed.length} / {MAX_REPORT_COMMENT_LENGTH}
            </p>
            <p className="report-note">通報したことや入力した内容は、相手には伝わりません。運営だけが確認します。</p>
            {error && (
              <p className="report-error" role="alert">
                {error}
              </p>
            )}
            <div className="report-buttons">
              <button type="button" className="report-cancel" disabled={sending} onClick={onClose}>
                キャンセル
              </button>
              <button type="button" className="report-send" disabled={!canSend} onClick={handleSend}>
                {sending ? '送信しています...' : '通報する'}
              </button>
            </div>
          </>
        ) : (
          <>
            <p className="report-done-title">{result === 'ACCEPTED' ? '通報を受け付けました' : 'すでに通報済みです'}</p>
            <p className="report-note">
              通報したことは相手には伝わりません。運営が内容を確認します。
              {blockable && '相手と関わりたくない場合は、ブロックもできます。'}
            </p>
            <div className="report-buttons">
              {blockable && (
                <button type="button" className="report-block" onClick={() => setConfirmingBlock(true)}>
                  @{owner.username} さんをブロックする
                </button>
              )}
              <button type="button" className="report-cancel" onClick={onClose}>
                閉じる
              </button>
            </div>
          </>
        )}
      </div>

      {confirmingBlock && (
        <BlockConfirmDialog
          title={blockText.title}
          body={blockText.body}
          confirmLabel="ブロックする"
          busyLabel="ブロックしています..."
          busy={blocking}
          error={blockError}
          onConfirm={handleBlock}
          onCancel={() => setConfirmingBlock(false)}
        />
      )}
    </div>
  )
}
