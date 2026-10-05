import { useEffect, useId, useLayoutEffect, useMemo, useRef, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { endConversation, sendMessage, type ConversationDetail, type Message } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { IMAGE_ACCEPT, validateImageFile } from '../../validation/postRules'
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
      <ChatHeader detail={detail} onEnded={(ended) => chat.setDetail((prev) => (prev ? { ...prev, ...ended } : prev))} />

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
              <Bubble message={message} detail={detail} onImageLoad={handleImageLoad} />
            </li>
          ))}
        </ol>
      </div>

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

/** 上部：戻る・相手（押すとプロフィール）・メニュー（会話を終了する） */
function ChatHeader({
  detail,
  onEnded,
}: {
  detail: ConversationDetail | null
  onEnded: (ended: Pick<ConversationDetail, 'status' | 'endedAt'>) => void
}) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const menuId = useId()
  const [menuOpen, setMenuOpen] = useState(false)
  const [confirming, setConfirming] = useState(false)
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
      setConfirming(false)
    } catch (err) {
      setError(err instanceof ApiError ? err.message : '会話を終了できませんでした。時間をおいて再度お試しください。')
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

      {/* 通報・ブロックは未実装のため、いまは「会話を終了する」だけ。操作がないときはメニューを出さない */}
      {canEnd && (
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
              <li role="none">
                <button
                  type="button"
                  role="menuitem"
                  className="chat-menu-item chat-menu-danger"
                  onClick={() => {
                    setMenuOpen(false)
                    setConfirming(true)
                  }}
                >
                  会話を終了する
                </button>
              </li>
            </ul>
          )}
        </div>
      )}

      {confirming && (
        <ConfirmDialog
          title="会話を終了しますか？"
          body="終了すると、この相手とメッセージを送り合えなくなります。この操作は取り消せません。"
          confirmLabel={ending ? '終了しています...' : '終了する'}
          busy={ending}
          error={error}
          onConfirm={handleEnd}
          onCancel={() => {
            setConfirming(false)
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

/** 吹き出し1つ。相手は左（アイコン付き・白）、自分は右（アクセント色）。時刻は吹き出しの外側の下 */
function Bubble({
  message,
  detail,
  onImageLoad,
}: {
  message: Message
  detail: ConversationDetail | null
  onImageLoad: () => void
}) {
  return (
    <div className={`chat-row ${message.mine ? 'chat-row-mine' : 'chat-row-theirs'}`}>
      {!message.mine && detail && <DmAvatar partner={detail.partner} />}
      <div className="chat-bubble-group">
        {message.imageUrl && (
          <img className="chat-image" src={message.imageUrl} alt="送信された画像" onLoad={onImageLoad} />
        )}
        {message.body !== null && <p className="chat-bubble">{message.body}</p>}
      </div>
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
      return 'この会話は終了しました（閲覧のみ）'
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
