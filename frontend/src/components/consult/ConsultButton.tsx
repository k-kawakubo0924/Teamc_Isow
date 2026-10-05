import { useEffect, useId, useRef, useState, type ReactNode } from 'react'
import { Link, useNavigate } from 'react-router'
import { ApiError } from '../../api/client'
import { requestConsultation, type ConsultationStatus } from '../../api/consultations'
import { useAuth } from '../../auth/authContext'
import './consult.css'

/** 一言メッセージの上限（バックエンドの ConsultationRequest.MAX_MESSAGE_LENGTH） */
const MAX_MESSAGE_LENGTH = 1000

/**
 * 「相談を申し込む」ボタン（相手のプロフィール・フォロー中一覧）。状態は呼び出し側で GET .../consultation-status から渡す。
 * - 申し込める：押すと一言メッセージの入力ダイアログを出し、申し込んだら「送信したリクエスト」へ移る
 * - 申請済み：押せない
 * - 相談中・相手から届いている：チャット・メッセージリクエストへのリンク
 * - 上限に達している・拒否から24時間以内：押せない（理由は consultNote で呼び出し側が表示する）
 *
 * @param status null は読み込み中・読み込めなかった場合（押せない状態で表示する）
 * @param label 申し込めるときの文言（プロフィールは「相談を申し込む」、フォロー中一覧は「相談する」）
 * @param icon 文言の前に付けるアイコン（プロフィールのみ）
 * @param onStatusChanged 申込が失敗したなど、状態を取り直してほしいときに呼ぶ
 */
export function ConsultButton({
  userId,
  username,
  status,
  label,
  icon,
  className,
  onStatusChanged,
}: {
  userId: number
  username: string
  status: ConsultationStatus | null
  label: string
  icon?: ReactNode
  className: string
  onStatusChanged: () => void
}) {
  const [open, setOpen] = useState(false)

  if (status?.reason === 'SELF') return null
  if (status?.reason === 'IN_PROGRESS' && status.conversationId !== null) {
    return (
      <Link to={`/dm/${status.conversationId}`} className={`${className} consult-link`}>
        相談中
      </Link>
    )
  }
  if (status?.reason === 'REQUEST_RECEIVED') {
    return (
      <Link to="/dm/requests" className={`${className} consult-link`} title="この相手からリクエストが届いています">
        リクエストあり
      </Link>
    )
  }

  const requested = status?.reason === 'ALREADY_REQUESTED'
  return (
    <>
      <button
        type="button"
        className={`${className}${requested ? ' consult-requested' : ''}`}
        disabled={status === null || !status.available}
        aria-label={requested ? `@${username} に申請済み` : `@${username} に${label}`}
        onClick={() => setOpen(true)}
      >
        {!requested && icon}
        {requested ? '申請済み' : label}
      </button>
      {open && (
        <ConsultDialog
          userId={userId}
          username={username}
          onClose={() => setOpen(false)}
          onStatusChanged={onStatusChanged}
        />
      )}
    </>
  )
}

/** 一言メッセージを入力して申し込むダイアログ */
function ConsultDialog({
  userId,
  username,
  onClose,
  onStatusChanged,
}: {
  userId: number
  username: string
  onClose: () => void
  onStatusChanged: () => void
}) {
  const { token } = useAuth()
  const navigate = useNavigate()
  const titleId = useId()
  const messageId = useId()
  const textareaRef = useRef<HTMLTextAreaElement>(null)
  const [message, setMessage] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    textareaRef.current?.focus()
  }, [])

  // Esc で閉じる（送信中は閉じない）
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !sending) onClose()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [sending, onClose])

  const handleSubmit = async () => {
    if (!token || sending) return
    setSending(true)
    setError(null)
    try {
      await requestConsultation(userId, message, token)
      navigate('/dm/sent')
    } catch (err) {
      if (err instanceof ApiError) {
        setError(err.body?.errors.message ?? err.message)
        // すでに申し込んでいた・上限に達したなど、状態が変わっていたらボタンの表示を取り直す
        if (err.status === 409) onStatusChanged()
      } else {
        setError('申し込めませんでした。時間をおいて再度お試しください。')
      }
      setSending(false)
    }
  }

  return (
    <div className="consult-backdrop">
      <div className="consult-dialog" role="dialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className="consult-title">
          @{username} に相談を申し込む
        </h2>
        <p className="consult-hint">相手が承認すると、メッセージを送り合えるようになります。</p>
        <label htmlFor={messageId} className="consult-label">
          一言メッセージ（任意）
        </label>
        <textarea
          id={messageId}
          ref={textareaRef}
          className="consult-textarea"
          rows={4}
          maxLength={MAX_MESSAGE_LENGTH}
          placeholder="相談したい内容を一言添えましょう"
          value={message}
          disabled={sending}
          onChange={(e) => setMessage(e.target.value)}
        />
        <p className="consult-counter">
          {message.length} / {MAX_MESSAGE_LENGTH}
        </p>
        {error && (
          <p className="consult-error" role="alert">
            {error}
          </p>
        )}
        <div className="consult-buttons">
          <button type="button" className="consult-cancel" disabled={sending} onClick={onClose}>
            キャンセル
          </button>
          <button type="button" className="consult-submit" disabled={sending} onClick={handleSubmit}>
            {sending ? '送信中...' : '申し込む'}
          </button>
        </div>
      </div>
    </div>
  )
}
