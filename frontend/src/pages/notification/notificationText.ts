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
 * - いいね：投稿の詳細画面がまだないため、いいねされた投稿が並ぶ自分のプロフィール（詳細画面ができたら /posts/{id} にする）
 * - フォロー：相手のプロフィール
 * - 相談・承認・拒否・メッセージ：その会話のチャット
 */
export function notificationLink(notification: NotificationItem): string {
  switch (notification.type) {
    case 'LIKED':
      return '/profile'
    case 'FOLLOWED':
      return `/users/${notification.actor.id}`
    default:
      return notification.conversationId === null ? '/notifications' : `/dm/${notification.conversationId}`
  }
}
