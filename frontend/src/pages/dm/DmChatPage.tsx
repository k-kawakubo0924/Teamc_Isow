import { useEffect, useState } from 'react'
import { useLocation, useNavigate, useParams } from 'react-router'
import { ApiError } from '../../api/client'
import { fetchConversation, type ConversationDetail } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { goBack } from '../settings/goBack'
import { DmAvatar } from './dmParts'
import './dm.css'

/**
 * チャット画面（/dm/{id}）。次のステップで作るため、いまは相手の名前と「準備中」だけを表示する。
 * 画面下部に入力欄を置く予定のため、下部ナビゲーションは表示しない
 */
export default function DmChatPage() {
  const { conversationId } = useParams()
  const id = conversationId !== undefined && /^\d+$/.test(conversationId) ? Number(conversationId) : null
  return <DmChatView key={id} id={id} />
}

function DmChatView({ id }: { id: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  const { token } = useAuth()
  const [conversation, setConversation] = useState<ConversationDetail | null>(null)
  const [error, setError] = useState<string | null>(id === null ? '会話が見つかりません。' : null)

  useEffect(() => {
    if (!token || id === null) return
    const controller = new AbortController()
    fetchConversation(id, token, controller.signal)
      .then(setConversation)
      .catch((err: unknown) => {
        if (controller.signal.aborted) return
        setError(err instanceof ApiError ? err.message : '会話を読み込めませんでした。')
      })
    return () => controller.abort()
  }, [id, token])

  return (
    <main className="dm-page">
      <header className="dm-sub-header">
        <button type="button" className="dm-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/dm')}>
          ‹
        </button>
        {conversation && (
          <h1 className="dm-chat-title">
            <DmAvatar partner={conversation.partner} />
            <span>@{conversation.partner.username}</span>
          </h1>
        )}
      </header>
      {error ? (
        <p className="dm-message" role="alert">
          {error}
        </p>
      ) : (
        conversation && <p className="dm-message">チャット画面は準備中です</p>
      )}
    </main>
  )
}
