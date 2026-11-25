import { useState } from 'react'
import { useDmSummary } from '../../components/dmSummaryContext'
import { ChatRow } from './ChatRow'
import { DmListFooter, DmSubHeader } from './dmParts'
import { useConversationList } from './useConversationList'
import './dm.css'

/**
 * ピン留めした相談の一覧（/dm/pinned。docs/dm.md「ピン留め」）。DM一覧の見出しの左上のマークから開く。
 * 自分がピン留めした相談（進行中・終了）だけを、DM一覧と同じく最後のメッセージの新しい順に出す
 */
export default function DmPinnedPage() {
  const { summary } = useDmSummary()
  const { state, retry, sentinelRef } = useConversationList('chats', '', true)
  // 経過時間はこの一覧を読み込んだ時点を基準にする（DM一覧と同じ）
  const [now] = useState(() => new Date())
  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <main className="dm-page">
      <DmSubHeader title="ピン留めした相談" count={summary?.pinnedCount ?? null} />
      {isEmpty ? (
        <p className="dm-message">ピン留めした相談はありません</p>
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
    </main>
  )
}
