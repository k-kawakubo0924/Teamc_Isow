import { useState } from 'react'
import { Link } from 'react-router'
import { ApiError } from '../../api/client'
import { respondToRequest } from '../../api/conversations'
import { useAuth } from '../../auth/authContext'
import { useDmSummary } from '../../components/dmSummaryContext'
import { timeAgo } from '../../utils/timeAgo'
import { DmAvatar, DmListFooter, DmSubHeader } from './dmParts'
import { useConversationList, type ConversationRow } from './useConversationList'
import './dm.css'

/**
 * 1件ごとの操作の状態。
 * pending は通信中、done は承認・拒否が済んだ、locked はこの画面ではもう操作できない（相手が取り消した・別の画面で操作済みなど）
 */
type RowState =
  | { phase: 'idle'; error: string | null }
  | { phase: 'pending' }
  | { phase: 'done'; action: 'accept' | 'reject' }
  | { phase: 'locked'; error: string }

const IDLE: RowState = { phase: 'idle', error: null }

/**
 * メッセージリクエスト（/dm/requests）。受け取った申請（申請中）を一覧にし、承認・拒否ができる。
 * 同じ申請に2回送ると2回目がエラーになるため、押した時点でその行のボタンを両方とも無効にする
 */
export default function DmRequestsPage() {
  const { summary } = useDmSummary()
  const { state, retry, sentinelRef } = useConversationList('received', '')
  const [now] = useState(() => new Date())
  const isEmpty = state.items.length === 0 && !state.hasNext && state.status === 'idle'

  return (
    <main className="dm-page">
      <DmSubHeader title="メッセージリクエスト" count={summary?.receivedRequestCount ?? null} />
      {isEmpty ? (
        <p className="dm-message">届いているリクエストはありません</p>
      ) : (
        <ul className="dm-list">
          {state.items.map((request) => (
            <li key={request.id}>
              <ReceivedRequest request={request} now={now} />
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

function ReceivedRequest({ request, now }: { request: ConversationRow; now: Date }) {
  const { token } = useAuth()
  const { refresh } = useDmSummary()
  const [rowState, setRowState] = useState<RowState>(IDLE)
  const disabled = rowState.phase !== 'idle'

  const handleRespond = async (action: 'accept' | 'reject') => {
    if (!token || disabled) return
    setRowState({ phase: 'pending' })
    try {
      await respondToRequest(request.id, action, token)
      setRowState({ phase: 'done', action })
      refresh()
    } catch (err) {
      setRowState(stateAfterError(err))
      refresh()
    }
  }

  return (
    <article className="dm-request-card">
      <Link to={`/users/${request.partner.id}`} className="dm-request-user">
        <DmAvatar partner={request.partner} />
        <span className="dm-row-text">
          <span className="dm-row-top">
            <span className="dm-username">@{request.partner.username}</span>
            <span className="dm-time">{timeAgo(request.requestedAt, now, { yesterday: true })}</span>
          </span>
          <span className="dm-request-message">{request.lastMessageBody ?? 'メッセージはありません'}</span>
        </span>
      </Link>

      {rowState.phase === 'done' ? (
        <p className="dm-request-result" role="status">
          {rowState.action === 'accept' ? (
            <>
              承認しました
              <Link to={`/dm/${request.id}`} className="dm-request-result-link">
                メッセージを送る ›
              </Link>
            </>
          ) : (
            '拒否しました'
          )}
        </p>
      ) : (
        <div className="dm-request-buttons">
          <button
            type="button"
            className="dm-reject"
            disabled={disabled}
            aria-label={`@${request.partner.username} のリクエストを拒否`}
            onClick={() => handleRespond('reject')}
          >
            拒否
          </button>
          <button
            type="button"
            className="dm-accept"
            disabled={disabled}
            aria-label={`@${request.partner.username} のリクエストを承認`}
            onClick={() => handleRespond('accept')}
          >
            {rowState.phase === 'pending' ? '送信中...' : '承認'}
          </button>
        </div>
      )}
      {(rowState.phase === 'idle' || rowState.phase === 'locked') && rowState.error && (
        <p className="dm-error" role="alert">
          {rowState.error}
        </p>
      )}
    </article>
  )
}

/**
 * 失敗したときの状態。やり直せる失敗（上限に達している・通信エラーなど）はボタンを押せる状態に戻し、
 * 状態が変わっていた（別の画面で操作済み・取り消された）場合は、押しても同じ結果になるため無効のままにする
 */
function stateAfterError(err: unknown): RowState {
  if (!(err instanceof ApiError)) {
    return { phase: 'idle', error: '操作できませんでした。時間をおいて再度お試しください。' }
  }
  if (err.status === 404 || err.body?.reason === 'INVALID_STATUS') {
    return { phase: 'locked', error: err.message }
  }
  return { phase: 'idle', error: err.message }
}
