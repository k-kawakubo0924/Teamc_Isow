import { useEffect, useState } from 'react'
import { Link } from 'react-router'
import { fetchConversations, type ConversationListItem } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { useDmSummary } from '../../components/dmSummaryContext'
import { timeAgo } from '../../utils/timeAgo'

/** 欄に出す会話の件数の上限 */
const MAX_ITEMS = 3

/**
 * ホームの新着メッセージ（docs/home.md「新着メッセージ」、design/home.png）。
 * やり取り中の会話のうち、相手から届いた未読メッセージがある会話を新しい順に出す。新着がなければ欄ごと出さない。
 * 各行には相手から届いた最新の未読を出し、自分が送ったメッセージは出さない。
 * 申請（リクエスト）は含めない（下部ナビのバッジと、DM一覧の「メッセージリクエスト」で分かるため）。
 * 下部ナビのバッジと同じ件数（未読メッセージの合計）が変わったら取り直す（バッジは最大1分ごとに取り直している）
 */
export function NewMessages() {
  const { token } = useAuth()
  const { summary } = useDmSummary()
  const unreadTotal = summary?.unreadMessageCount
  const [items, setItems] = useState<ConversationListItem[]>([])

  useEffect(() => {
    if (!token) return
    const controller = new AbortController()
    fetchConversations('chats', '', 0, token, controller.signal, { unreadOnly: true, size: MAX_ITEMS })
      .then((result) => setItems(result.conversations))
      .catch(() => {
        // 読めなくてもホームは使える。前回の内容を出したままにし、次に件数が変わったときに取り直す
      })
    return () => controller.abort()
  }, [token, unreadTotal])

  if (items.length === 0) return null

  return (
    <section className="home-messages" aria-labelledby="home-messages-title">
      <div className="home-messages-head">
        <h2 id="home-messages-title" className="home-messages-title">
          新着メッセージ
        </h2>
        <Link to="/dm" className="home-messages-all">
          すべて見る
        </Link>
      </div>
      <ul className="home-messages-list">
        {items.map((item) => (
          <li key={item.conversationId}>
            <MessageRow item={item} />
          </li>
        ))}
      </ul>
    </section>
  )
}

/** 相手から届いた最新の未読メッセージを出す（自分が後から送ったメッセージは出さない） */
function MessageRow({ item }: { item: ConversationListItem }) {
  const unread = item.latestUnread
  const preview = unread?.body ?? (unread?.hasImage ? '画像が送信されました' : '')
  return (
    <Link to={`/dm/${item.conversationId}`} className="home-message">
      {item.partner.profileImageUrl ? (
        <img className="home-message-avatar" src={item.partner.profileImageUrl} alt="" />
      ) : (
        <span className="home-message-avatar home-message-avatar-empty" aria-hidden="true">
          <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
            <circle cx="12" cy="8.5" r="3.5" />
            <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
          </svg>
        </span>
      )}
      <span className="home-message-text">
        <span className="home-message-top">
          <span className="home-message-name">@{item.partner.username}</span>
          {unread && <span className="home-message-time">{timeAgo(unread.sentAt)}</span>}
        </span>
        <span className="home-message-preview">{preview}</span>
      </span>
      <span className="home-message-badge" aria-label={`未読 ${item.unreadCount}件`}>
        {item.unreadCount}
      </span>
    </Link>
  )
}
