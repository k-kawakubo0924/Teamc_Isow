import type { NotificationItem, NotificationType } from '../../api/notifications'

/**
 * 通知の文面のうち、「@ユーザー名」の後ろに続く部分（docs/notification.md「通知の文面」）。
 * 文面はサーバーで組み立てず、ここで組み立てる（後から文面を変えやすくするため）
 */
const TEXTS: Record<NotificationType, string> = {
  LIKED: 'さんがあなたの投稿にいいねしました',
  FOLLOWED: 'さんがあなたをフォローしました',
  CONSULTATION_REQUESTED: 'さんから相談が届いています',
  CONSULTATION_APPROVED: 'さんが相談を承認しました',
  CONSULTATION_REJECTED: 'さんへの相談は承認されませんでした',
  MESSAGE_RECEIVED: 'さんからメッセージが届いています',
}

/** 通知の文面の、ユーザー名の後ろの部分。知らない種類（画面より新しいサーバー）は汎用の文面にする */
export function notificationText(type: NotificationType): string {
  return TEXTS[type] ?? 'さんからお知らせがあります'
}

/**
 * 通知をタップしたときの遷移先（docs/notification.md）。
 * - いいね：いいねされた投稿の詳細画面
 * - フォロー：相手のプロフィール
 * - 相談・承認・拒否・メッセージ：その会話のチャット
 */
export function notificationLink(notification: NotificationItem): string {
  switch (notification.type) {
    case 'LIKED':
      // いいねの通知は必ず投稿を持つ（投稿を削除すると通知も消える）。念のため、ない場合は自分の投稿一覧へ
      return notification.post === null ? '/profile' : `/posts/${notification.post.id}`
    case 'FOLLOWED':
      return `/users/${notification.actor.id}`
    default:
      return notification.conversationId === null ? '/notifications' : `/dm/${notification.conversationId}`
  }
}

/**
 * お知らせの発行日（"2026-10-08T09:05:00" を「2026/10/08」の形にする）。
 * お知らせは古いものも残るため、「〇日前」ではなく日付で出す。サーバーの日付をそのまま出す（ブラウザのタイムゾーンで変換しない）
 */
export function announcementDate(value: string): string {
  const m = value.match(/^(\d{4})-(\d{2})-(\d{2})/)
  return m === null ? value : `${m[1]}/${m[2]}/${m[3]}`
}
