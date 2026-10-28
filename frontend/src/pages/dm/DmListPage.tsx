import { useEffect, useId, useState } from 'react'
import { Link } from 'react-router'
import logo from '../../assets/logo.png'
import { useDmSummary } from '../../components/dmSummaryContext'
import { ChatRow, PinIcon } from './ChatRow'
import { DmListFooter } from './dmParts'
import { useConversationList } from './useConversationList'
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
        {/*
          左上のマークは、ピン留めした相談の一覧を開くもの（design/DMlist.png。docs/dm.md「DM一覧の見出し」）。
          ピン留めを作るまでいったん削除していたものを戻した。通知ベルではない（通知一覧にはつなげない）。
          バッジはピン留めの件数で、未読ではないため、設計画像と違い未読のバッジとは別の見た目にする
          （未読は「対応が必要」の合図で、ピン留めの件数は変わらない数字のため。同じ色だと見間違える）
        */}
        <Link
          to="/dm/pinned"
          className="dm-pin-link"
          aria-label={summary !== null && summary.pinnedCount > 0 ? `ピン留めした相談（${summary.pinnedCount}件）` : 'ピン留めした相談'}
        >
          <PinIcon size={22} />
          {summary !== null && summary.pinnedCount > 0 && (
            <span className="dm-pin-badge" aria-hidden="true">
              {summary.pinnedCount}
            </span>
          )}
        </Link>
        <h1 className="dm-logo">
          <img className="dm-logo-image" src={logo} alt="ISHO" />
        </h1>
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
