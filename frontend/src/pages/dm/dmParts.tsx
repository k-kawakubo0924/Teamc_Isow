import type { RefObject } from 'react'
import { useLocation, useNavigate } from 'react-router'
import type { ConversationPartner } from '../../api/conversations'
import { goBack } from '../settings/goBack'

/** 相手のアイコン。未設定なら人の形のアイコン */
export function DmAvatar({ partner }: { partner: ConversationPartner }) {
  return partner.profileImageUrl ? (
    <img className="dm-avatar" src={partner.profileImageUrl} alt="" />
  ) : (
    <span className="dm-avatar dm-avatar-empty" aria-hidden="true">
      <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.4">
        <circle cx="12" cy="8.5" r="3.5" />
        <path d="M5 20c1.1-3.4 3.7-5 7-5s5.9 1.6 7 5" strokeLinecap="round" />
      </svg>
    </span>
  )
}

/** メッセージリクエスト・送信したリクエストの見出し（戻るボタンと件数） */
export function DmSubHeader({ title, count }: { title: string; count: number | null }) {
  const navigate = useNavigate()
  const location = useLocation()
  return (
    <header className="dm-sub-header">
      <button type="button" className="dm-back" aria-label="戻る" onClick={() => goBack(navigate, location, '/dm')}>
        ‹
      </button>
      <h1 className="dm-sub-title">
        {title}
        {count !== null && <span className="dm-sub-count">{count.toLocaleString('ja-JP')}件</span>}
      </h1>
    </header>
  )
}

/** 一覧の読み込み中・エラー・無限スクロールの目印 */
export function DmListFooter({
  status,
  errorMessage,
  hasNext,
  onRetry,
  sentinelRef,
}: {
  status: 'idle' | 'loading' | 'error'
  errorMessage: string | null
  hasNext: boolean
  onRetry: () => void
  sentinelRef: RefObject<HTMLDivElement | null>
}) {
  return (
    <>
      {status === 'loading' && (
        <p className="dm-message" role="status">
          読み込み中...
        </p>
      )}
      {status === 'error' && (
        <div className="dm-message" role="alert">
          <p>{errorMessage}</p>
          <button type="button" className="dm-retry" onClick={onRetry}>
            もう一度読み込む
          </button>
        </div>
      )}
      {/* 次のページがある間だけ置く、無限スクロールの目印 */}
      {hasNext && <div ref={sentinelRef} className="dm-sentinel" aria-hidden="true" />}
    </>
  )
}
