import { Link } from 'react-router'
import { timeAgo } from '../../utils/timeAgo'
import { DmAvatar } from './dmParts'
import type { ConversationRow } from './useConversationList'

/**
 * やり取り中の会話1件（DM一覧・ピン留めした相談の一覧）。行の色はデザインに合わせて、
 * 未読あり（白）・既読（ベージュ）・終了（灰色）で分ける。押すとチャット画面（/dm/{id}）へ移る。
 * 自分がピン留めした相談にはピンの印を、通知をオフにした相談には通知オフの印を出す（docs/dm.md「ピン留め」「通知オフ」。相手には分からない）
 */
export function ChatRow({ conversation, now }: { conversation: ConversationRow; now: Date }) {
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
          {conversation.pinned && (
            <span className="dm-row-mark" role="img" aria-label="ピン留め中">
              <PinIcon size={12} />
            </span>
          )}
          {conversation.muted && (
            <span className="dm-row-mark" role="img" aria-label="通知オフ">
              <MutedIcon />
            </span>
          )}
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

/** 通知オフの印（斜線の入ったベル） */
function MutedIcon() {
  return (
    <svg width="12" height="12" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M6 16.5V11a6 6 0 0 1 10.5-4M18 11v5.5l1.5 2H8" />
      <path d="M10 20.5a2 2 0 0 0 4 0M4 4l16 16" />
    </svg>
  )
}

/** ピンの形のアイコン（DM一覧の見出しのマーク・行の印） */
export function PinIcon({ size }: { size: number }) {
  return (
    <svg width={size} height={size} viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.6" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
      <path d="M9 4h6l-1 6 3 3H7l3-3z" />
      <path d="M12 13v7" />
    </svg>
  )
}
