import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router'
import { useDmSummary } from '../../components/dmSummaryContext'
import { timeAgo } from '../../utils/timeAgo'
import { DmAvatar, DmListFooter } from './dmParts'
import { useConversationList, type ConversationRow } from './useConversationList'
import './dm.css'

/** 検索欄の入力が止まってから検索するまでの時間（1文字ごとに API を呼ばないため。フォロー一覧と同じ） */
const SEARCH_DELAY_MS = 300

/**
 * DM一覧（/dm）。docs/dm.md「DM一覧」、design/DMlist.png。
 * やり取り中の会話（進行中・終了）を、最終メッセージの新しい順に並べる。
 * 申請中の会話は「メッセージリクエスト」「送信したリクエスト」の画面に分ける
 */
export default function DmListPage() {
  const { summary } = useDmSummary()
  const searchId = useId()
  const [input, setInput] = useState('')
  const [query, setQuery] = useState('')

  // 入力が止まってから検索する
  useEffect(() => {
    const timer = setTimeout(() => setQuery(input.trim()), SEARCH_DELAY_MS)
    return () => clearTimeout(timer)
  }, [input])

  return (
    <main className="dm-page">
      <header className="dm-header">
        {/* 通知一覧（docs/notification.md）ができるまでは表示のみ（ホームと同じ） */}
        <span className="dm-bell" role="img" aria-label="通知">
          <svg width="22" height="22" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
            <path d="M6 16.5V11a6 6 0 0 1 12 0v5.5l1.5 2H4.5z" />
            <path d="M10 20.5a2 2 0 0 0 4 0" strokeLinecap="round" />
          </svg>
        </span>
        <h1 className="dm-logo">ISHO</h1>
      </header>

      <Link to="/dm/requests" className="dm-request-link">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinejoin="round" aria-hidden="true">
          <rect x="3.5" y="5.5" width="17" height="13" rx="1.5" />
          <path d="m4 6.5 8 6 8-6" />
        </svg>
        <span className="dm-request-label">メッセージリクエスト</span>
        {summary !== null && summary.receivedRequestCount > 0 && (
          <span className="dm-count-badge" aria-label={`${summary.receivedRequestCount}件`}>
            {summary.receivedRequestCount}
          </span>
        )}
        <span className="dm-chevron" aria-hidden="true">
          ›
        </span>
      </Link>

      <div className="dm-search-wrap">
        <label className="dm-search" htmlFor={searchId}>
          <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" aria-hidden="true">
            <circle cx="11" cy="11" r="6.5" />
            <path d="m20 20-4.2-4.2" strokeLinecap="round" />
          </svg>
          <input
            id={searchId}
            type="search"
            placeholder="名前で検索"
            aria-label="名前で検索"
            maxLength={50}
            value={input}
            onChange={(e) => setInput(e.target.value)}
          />
        </label>
      </div>

      {/* 検索語を変えたら一覧を作り直す */}
      <ChatList key={query} query={query} />

      <Link to="/dm/sent" className="dm-sent-link">
        <svg width="18" height="18" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
          <path d="M4 12h12M12 7l5 5-5 5M20 5v14" />
        </svg>
        <span className="dm-sent-label">送信したリクエスト</span>
        {summary !== null && <span className="dm-sent-count">{summary.sentRequestCount}件</span>}
        <span className="dm-chevron" aria-hidden="true">
          ›
        </span>
      </Link>
    </main>
  )
}

function ChatList({ query }: { query: string }) {
  const { state, retry, sentinelRef } = useConversationList('chats', query)
  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'
  // 経過時間はこの一覧を読み込んだ時点を基準にする（表示中に少しずつずれるのは許容する）
  const [now] = useState(() => new Date())

  return (
    <>
      {isEmpty ? (
        <p className="dm-message">
          {query !== '' ? `「${query}」に一致するユーザーはいません` : 'やり取り中の会話はありません'}
        </p>
      ) : (
        <ul className="dm-list">
          {state.items.map((conversation) => (
            <li key={conversation.id}>
              <ChatRow conversation={conversation} now={now} />
            </li>
          ))}
        </ul>
      )}
      <DmListFooter
        status={state.status}
        errorMessage={state.errorMessage}
        hasNext={state.hasNext}
        onRetry={retry}
        sentinelRef={sentinelRef}
      />
    </>
  )
}

/**
 * 会話1件。行の色はデザインに合わせて、未読あり（白）・既読（ベージュ）・終了（灰色）で分ける。
 * 押すとチャット画面（/dm/{id}）へ移る
 */
function ChatRow({ conversation, now }: { conversation: ConversationRow; now: Date }) {
  const ended = conversation.status === 'ENDED'
  const variant = ended ? 'ended' : conversation.unreadCount > 0 ? 'unread' : 'read'
  const preview =
    conversation.lastMessageBody ??
    (conversation.lastMessageHasImage ? '画像が送信されました' : 'メッセージはまだありません')
  // メッセージがない会話は、申し込んだ日時からの経過時間を出す
  const at = conversation.lastMessageAt ?? conversation.requestedAt

  return (
    <Link to={`/dm/${conversation.id}`} className={`dm-row dm-row-${variant}`}>
      <DmAvatar partner={conversation.partner} />
      <span className="dm-row-text">
        <span className="dm-row-top">
          <span className="dm-username">@{conversation.partner.username}</span>
          <span className="dm-time">{timeAgo(at, now, { yesterday: true })}</span>
          {ended && <span className="dm-status-label">終了</span>}
        </span>
        <span className="dm-preview">{preview}</span>
      </span>
      {conversation.unreadCount > 0 && (
        <span className="dm-count-badge" aria-label={`未読 ${conversation.unreadCount}件`}>
          {conversation.unreadCount}
        </span>
      )}
    </Link>
  )
}
