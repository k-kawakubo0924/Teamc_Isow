import { useState } from 'react'
import { Link } from 'react-router'
import { useDmSummary } from '../../components/dmSummaryContext'
import { timeAgo } from '../../utils/timeAgo'
import { DmAvatar, DmListFooter, DmSubHeader } from './dmParts'
import { useConversationList } from './useConversationList'
import './dm.css'

/**
 * 送信したリクエスト（/dm/sent）。自分が送った申請のうち、承認待ち（申請中）のものを一覧にする。
 * 拒否された申請の見せ方は未確定（docs/dm.md「未確定・要確認」）のため、ここには出さない
 */
export default function DmSentPage() {
  const { summary } = useDmSummary()
  const { state, retry, sentinelRef } = useConversationList('sent', '')
  const [now] = useState(() => new Date())
  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <main className="dm-page">
      <DmSubHeader title="送信したリクエスト" count={summary?.sentRequestCount ?? null} />
      {isEmpty ? (
        <p className="dm-message">承認待ちのリクエストはありません</p>
      ) : (
        <ul className="dm-list">
          {state.items.map((request) => (
            <li key={request.id}>
              <Link to={`/users/${request.partner.id}`} className="dm-row dm-row-read">
                <DmAvatar partner={request.partner} />
                <span className="dm-row-text">
                  <span className="dm-row-top">
                    <span className="dm-username">@{request.partner.username}</span>
                    <span className="dm-time">{timeAgo(request.requestedAt, now, { yesterday: true })}に申請</span>
                    <span className="dm-status-label">承認待ち</span>
                  </span>
                  <span className="dm-preview">{request.lastMessageBody ?? 'メッセージなし'}</span>
                </span>
              </Link>
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
