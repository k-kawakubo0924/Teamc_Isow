import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { blockUser } from '../../api/blocks'
import { ApiError } from '../../api/client'
import { endConversation, sendMessage, type ConversationDetail, type Message } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { IMAGE_ACCEPT, validateImageFile } from '../../validation/postRules'
import { blockConfirmText } from '../../components/block/blockText'
import { ReportDialog } from '../../components/report/ReportDialog'
import { goBack } from '../settings/goBack'
import { DmAvatar } from './dmParts'
import { useChat } from './useChat'
import './dm.css'
import './chat.css'

/** messages.body の列の長さと同じ（バックエンドの MessageSendRequest） */
const MAX_BODY_LENGTH = 1000

/** 一番下からこの距離（px）以内を見ているときは、新しいメッセージが届いたら一番下までスクロールする */
const NEAR_BOTTOM_PX = 80

/** 上端からこの距離（px）以内までスクロールしたら、さらに古いメッセージを読む */
const LOAD_OLDER_PX = 60

/**
 * チャット画面（/dm/{id}）。docs/dm.md「チャット画面」、design/Chatscreen.png。
 * 画面下部に入力欄を置くため、下部ナビゲーションは表示しない
 */
export default function DmChatPage() {
  const { conversationId } = useParams()
  const id = conversationId !== undefined && /^\d+$/.test(conversationId) ? Number(conversationId) : null
  if (id === null) {
    return <ChatError message="会話が見つかりません。" />
  }
  // 別の会話に移ったら、読み込んだ内容を作り直す
  return <ChatView key={id} id={id} />
}

function ChatView({ id }: { id: number }) {
  const { token } = useAuth()
  const chat = useChat(id, token)
  const { detail, messages } = chat
  const scrollRef = useRef<HTMLDivElement>(null)
  const nearBottomRef = useRef(true)
  // 古いメッセージを先頭に足す前の、スクロールの高さと位置（足した後に、見ていた位置へ戻すため）
  const anchorRef = useRef<{ height: number; top: number } | null>(null)
  const edgesRef = useRef<{ first: number | null; last: number | null }>({ first: null, last: null })
  // 押した相手のメッセージ（下に「このメッセージを通報」を出す）と、通報の入力を開いているメッセージ（docs/admin.md「通報」）
  const [selectedMessageId, setSelectedMessageId] = useState<number | null>(null)
  const [reportingMessageId, setReportingMessageId] = useState<number | null>(null)

  const scrollToBottom = () => {
    const el = scrollRef.current
    if (el) el.scrollTop = el.scrollHeight
  }

  // メッセージが増えたときのスクロール位置。描画の直後（画面に出る前）に合わせる
  useLayoutEffect(() => {
    const el = scrollRef.current
    if (!el) return
    const first = messages.length > 0 ? messages[0].id : null
    const last = messages.length > 0 ? messages[messages.length - 1].id : null
    const prev = edgesRef.current
    edgesRef.current = { first, last }

    if (prev.last === null) {
      // 開いたとき：最新のメッセージが見える一番下にする
      scrollToBottom()
    } else if (anchorRef.current && first !== prev.first) {
      // 古いメッセージを先頭に足したとき：見ていたメッセージが同じ位置に見えるようにする
      el.scrollTop = el.scrollHeight - anchorRef.current.height + anchorRef.current.top
      anchorRef.current = null
    } else if (last !== prev.last) {
      // 新しいメッセージ：一番下付近を見ているとき、または自分が送ったときだけ一番下へ
      if (nearBottomRef.current || messages[messages.length - 1].mine) scrollToBottom()
    }
  }, [messages])

  const handleScroll = () => {
    const el = scrollRef.current
    if (!el) return
    nearBottomRef.current = el.scrollHeight - el.scrollTop - el.clientHeight < NEAR_BOTTOM_PX
    if (el.scrollTop < LOAD_OLDER_PX && chat.hasOlder && !chat.loadingOlder) {
      anchorRef.current = { height: el.scrollHeight, top: el.scrollTop }
      void chat.loadOlder()
    }
  }

  // 画像は読み込み終わると高さが変わるため、一番下を見ていたら合わせ直す
  const handleImageLoad = () => {
    if (nearBottomRef.current) scrollToBottom()
  }

  if (chat.loadError) {
    return <ChatError message={chat.loadError} />
  }

  return (
    <main className="chat-page">
      <ChatHeader
        detail={detail}
        onEnded={(ended) => chat.setDetail((prev) => (prev ? { ...prev, ...ended } : prev))}
        onBlocked={() => void chat.poll()}
      />

      <div className="chat-messages" ref={scrollRef} onScroll={handleScroll}>
        {chat.loadingOlder && (
          <p className="chat-note" role="status">
            読み込み中...
          </p>
        )}
        {detail && messages.length === 0 && <p className="chat-note">メッセージはまだありません</p>}
        <ol className="chat-list">
          {messages.map((message) => (
            <li key={message.id}>
              <Bubble
                message={message}
                detail={detail}
                onImageLoad={handleImageLoad}
                selected={selectedMessageId === message.id}
                onSelect={() => setSelectedMessageId((prev) => (prev === message.id ? null : message.id))}
                onReport={() => {
                  setSelectedMessageId(null)
                  setReportingMessageId(message.id)
                }}
              />
            </li>
          ))}
        </ol>
      </div>

      {detail && reportingMessageId !== null && (
        <ReportDialog
          targetType="MESSAGE"
          targetId={reportingMessageId}
          owner={{ id: detail.partner.id, username: detail.partner.username }}
          blockable
          onClose={() => setReportingMessageId(null)}
          onBlocked={() => void chat.poll()}
        />
      )}

      {detail && (
        <ChatComposer
          detail={detail}
          onSent={(message) => chat.append([message])}
          onStatusChanged={() => void chat.poll()}
        />
      )}
    </main>
  )
}

function ChatError({ message }: { message: string }) {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <main className="chat-page">
      <header className="chat-header">
        <button type="button" className="dm-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/dm')}>
          ‹
        </button>
      </header>
      <p className="dm-message" role="alert">
        {message}
      </p>
    </main>
  )
}

/**
 * 上部：戻る・相手（押すとプロフィール）・メニュー（会話を終了する・ブロック）。
 * ブロックは会話の状態に関係なくできる。ブロックすると進行中の相談は終了するため、会話の状態を取り直す（onBlocked。docs/dm.md「ブロック」）
 */
function ChatHeader({
  detail,
  onEnded,
  onBlocked,
}: {
  detail: ConversationDetail | null
  onEnded: (ended: Pick<ConversationDetail, 'status' | 'endedAt'>) => void
  onBlocked: () => void
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const menuId = useId()
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirming, setConfirming] = useState<'end' | 'block' | null>(null)
  const [ending, setEnding] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const canEnd = detail?.status === 'ACTIVE'

  const handleEnd = async () => {
    if (!token || !detail || ending) return
    setEnding(true)
    setError(null)
    try {
      const result = await endConversation(detail.conversationId, token)
      onEnded({ status: result.status, endedAt: result.endedAt })
      setConfirming(null)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '会話を終了できませんでした。時間をおいて再度お試しください。')
    } finally {
      setEnding(false)
    }
  }

  /** ブロックする。通信中は「終了」と同じく ending で押せなくする */
  const handleBlock = async () => {
    if (!token || !detail || ending) return
    setEnding(true)
    setError(null)
    try {
      await blockUser(detail.partner.id, token)
      setConfirming(null)
      onBlocked()
    } catch (err) {
      setError(err instanceof ApiError ? err.message : 'ブロックできませんでした。時間をおいて再度お試しください。')
    } finally {
      setEnding(false)
    }
  }

  return (
    <header className="chat-header">
      <button type="button" className="dm-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/dm')}>
        ‹
      </button>
      {detail && (
        <Link to={`/users/${detail.partner.id}`} className="chat-partner">
          <DmAvatar partner={detail.partner} />
          <span className="chat-partner-name">@{detail.partner.username}</span>
        </Link>
      )}

      {/* 通報は次の段階で作る。いまは「会話を終了する」（進行中のときだけ）と「ブロック」 */}
      {detail && (
        <div className="chat-menu-wrap">
          <button
            type="button"
            className="chat-menu-button"
            aria-label="メニュー"
            aria-haspopup="menu"
            aria-expanded={menuOpen}
            aria-controls={menuId}
            onClick={() => setMenuOpen((open) => !open)}
          >
            <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" aria-hidden="true">
              <path d="M5 7h14M5 12h14M5 17h14" />
            </svg>
          </button>
          {menuOpen && (
            <ul className="chat-menu" id={menuId} role="menu">
              {canEnd && (
                <li role="none">
                  <button
                    type="button"
                    role="menuitem"
                    className="chat-menu-item chat-menu-danger"
                    onClick={() => {
                      setMenuOpen(false)
                      setConfirming('end')
                    }}
                  >
                    会話を終了する
                  </button>
                </li>
              )}
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  className="chat-menu-item chat-menu-danger"
                  onClick={() => {
                    setMenuOpen(false)
                    setConfirming('block')
                  }}
                >
                  ブロック
                </button>
              </li>
            </ul>
          )}
        </div>
      )}

      {confirming === 'end' && (
        <ConfirmDialog
          title="会話を終了しますか？"
          body="終了すると、この相手とメッセージを送り合えなくなります。この操作は取り消せません。"
          confirmLabel={ending ? '終了しています...' : '終了する'}
          busy={ending}
          error={error}
          onConfirm={handleEnd}
          onCancel={() => {
            setConfirming(null)
            setError(null)
          }}
        />
      )}
      {confirming === 'block' && detail && (
        <ConfirmDialog
          {...blockConfirmText(detail.partner.username)}
          confirmLabel={ending ? 'ブロックしています...' : 'ブロックする'}
          busy={ending}
          error={error}
          onConfirm={handleBlock}
          onCancel={() => {
            setConfirming(null)
            setError(null)
          }}
        />
      )}
    </header>
  )
}

/** 取り消せない操作の確認（ブラウザの confirm ではなく、画面のデザインに合わせたもの） */
function ConfirmDialog({
  title,
  body,
  confirmLabel,
  busy,
  error,
  onConfirm,
  onCancel,
}: {
  title: string
  body: string
  confirmLabel: string
  busy: boolean
  error: string | null
  onConfirm: () => void
  onCancel: () => void
}) {
  const titleId = useId()
  const cancelRef = useRef<HTMLButtonElement>(null)

  // 開いたら「キャンセル」に移動する（誤って Enter で終了しないため）
  useEffect(() => {
    cancelRef.current?.focus()
  }, [])

  // Esc で閉じる（終了の通信中は閉じない）
  useEffect(() => {
    const handleKey = (e: KeyboardEvent) => {
      if (e.key === 'Escape' && !busy) onCancel()
    }
    document.addEventListener('keydown', handleKey)
    return () => document.removeEventListener('keydown', handleKey)
  }, [busy, onCancel])

  return (
    <div className="chat-dialog-backdrop">
      <div className="chat-dialog" role="alertdialog" aria-modal="true" aria-labelledby={titleId}>
        <h2 id={titleId} className="chat-dialog-title">
          {title}
        </h2>
        <p className="chat-dialog-body">{body}</p>
        {error && (
          <p className="dm-error" role="alert">
            {error}
          </p>
        )}
        <div className="chat-dialog-buttons">
          <button type="button" ref={cancelRef} className="chat-dialog-cancel" disabled={busy} onClick={onCancel}>
            キャンセル
          </button>
          <button type="button" className="chat-dialog-confirm" disabled={busy} onClick={onConfirm}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}

/**
 * 吹き出し1つ。相手は左（アイコン付き・白）、自分は右（アクセント色）。時刻は吹き出しの外側の下。
 * 相手のメッセージは押すと、下に「このメッセージを通報」を出す（docs/admin.md「通報」。自分のメッセージは通報できない）
 */
function Bubble({
  message,
  detail,
  onImageLoad,
  selected,
  onSelect,
  onReport,
}: {
  message: Message
  detail: ConversationDetail | null
  onImageLoad: () => void
  selected: boolean
  onSelect: () => void
  onReport: () => void
}) {
  const content = (
    <>
      {message.imageUrl && (
        <img className="chat-image" src={message.imageUrl} alt="送信された画像" onLoad={onImageLoad} />
      )}
      {message.body !== null && <p className="chat-bubble">{message.body}</p>}
    </>
  )
  return (
    <div className={`chat-row ${message.mine ? 'chat-row-mine' : 'chat-row-theirs'}`}>
      {!message.mine && detail && <DmAvatar partner={detail.partner} />}
      {message.mine ? (
        <div className="chat-bubble-group">{content}</div>
      ) : (
        <div className="chat-bubble-group">
          <button type="button" className="chat-bubble-select" aria-expanded={selected} onClick={onSelect}>
            {content}
          </button>
          {selected && (
            <button type="button" className="chat-report-button" onClick={onReport}>
              このメッセージを通報
            </button>
          )}
        </div>
      )}
      <time className="chat-time" dateTime={message.sentAt}>
        {formatSentAt(message.sentAt)}
      </time>
    </div>
  )
}

/** 下部：＋（画像）・入力欄・送信ボタン。送れない状態（申請中・終了）では理由を出して無効にする */
function ChatComposer({
  detail,
  onSent,
  onStatusChanged,
}: {
  detail: ConversationDetail
  onSent: (message: Message) => void
  onStatusChanged: () => void
}) {
  const { token } = useAuth()
  const fileInputRef = useRef<HTMLInputElement>(null)
  const [text, setText] = useState('')
  const [image, setImage] = useState<File | null>(null)
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // 選んだ画像のプレビュー用 URL。使い終わったら（画像を変えた・画面を離れた）解放する
  const preview = useMemo(() => (image ? URL.createObjectURL(image) : null), [image])
  useEffect(() => {
    if (!preview) return
    return () => URL.revokeObjectURL(preview)
  }, [preview])

  const notice = disabledReason(detail)
  if (notice) {
    return (
      <footer className="chat-composer chat-composer-disabled">
        <p className="chat-disabled-reason">
          {notice}
          {detail.status === 'REQUESTED' && !detail.requestedByMe && (
            <Link to="/dm/requests" className="chat-disabled-link">
              リクエストを確認する ›
            </Link>
          )}
        </p>
      </footer>
    )
  }

  const canSend = !sending && (text.trim() !== '' || image !== null)

  const handleSelectImage = (file: File | undefined) => {
    if (!file) return
    const problem = validateImageFile(file)
    setError(problem)
    if (!problem) setImage(file)
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    if (!token || !canSend) return
    setSending(true)
    setError(null)
    try {
      const message = await sendMessage(detail.conversationId, text, image, token)
      onSent(message)
      setText('')
      setImage(null)
    } catch (err) {
      if (err instanceof ApiError) {
        const fieldError = err.body?.errors.body ?? err.body?.errors.image
        setError(fieldError ?? err.message)
        // 相手が終了したなど、会話の状態が変わっていた場合は、状態を取り直して入力欄を切り替える
        if (err.body?.reason === 'INVALID_STATUS') onStatusChanged()
      } else {
        setError('送信できませんでした。時間をおいて再度お試しください。')
      }
    } finally {
      setSending(false)
    }
  }

  return (
    <footer className="chat-composer">
      {error && (
        <p className="chat-composer-error" role="alert">
          {error}
        </p>
      )}
      {preview && (
        <div className="chat-preview">
          <img src={preview} alt="送信する画像" />
          <button type="button" className="chat-preview-remove" aria-label="画像を取り消す" disabled={sending} onClick={() => setImage(null)}>
            ×
          </button>
        </div>
      )}
      <form className="chat-form" onSubmit={handleSubmit}>
        <button
          type="button"
          className="chat-attach"
          aria-label="画像を選ぶ"
          disabled={sending}
          onClick={() => fileInputRef.current?.click()}
        >
          ＋
        </button>
        <input
          ref={fileInputRef}
          type="file"
          accept={IMAGE_ACCEPT}
          hidden
          onChange={(e) => {
            handleSelectImage(e.target.files?.[0])
            // 同じファイルを選び直しても選択として扱えるよう、選択を空に戻す
            e.target.value = ''
          }}
        />
        <input
          type="text"
          className="chat-input"
          placeholder="メッセージを入力"
          aria-label="メッセージを入力"
          maxLength={MAX_BODY_LENGTH}
          value={text}
          onChange={(e) => setText(e.target.value)}
          onKeyDown={(e) => {
            // 日本語入力の変換を確定する Enter では送信しない
            if (e.key === 'Enter' && e.nativeEvent.isComposing) e.preventDefault()
          }}
        />
        <button type="submit" className="chat-send" aria-label="送信" disabled={!canSend}>
          <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" aria-hidden="true">
            <path d="M5 3.5v17l15-8.5z" />
          </svg>
        </button>
      </form>
    </footer>
  )
}

/** 送れない理由。送れる（進行中）なら null */
function disabledReason(detail: ConversationDetail): string | null {
  switch (detail.status) {
    case 'ACTIVE':
      return null
    case 'REQUESTED':
      return detail.requestedByMe
        ? '相手が承認すると、メッセージを送れるようになります'
        : 'リクエストを承認すると、メッセージを送れるようになります'
    case 'ENDED':
      // 自動終了だけは理由を出す（相手が終えたように見えないように）。ほかの理由（ブロック・利用停止など）はサーバーも返さない
      return detail.endedByInactivity
        ? '30日間やり取りがなかったため、自動で終了しました（閲覧のみ）'
        : 'この会話は終了しました（閲覧のみ）'
    case 'REJECTED':
      return 'このリクエストは承認されませんでした'
  }
}

/** 送信時刻。今日なら「14:02」、それより前なら「10/4 14:02」 */
function formatSentAt(sentAt: string, now: Date = new Date()): string {
  const date = new Date(sentAt)
  const time = `${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`
  return date.toDateString() === now.toDateString() ? time : `${date.getMonth() + 1}/${date.getDate()} ${time}`
}
