import { getJson, sendWithToken } from './client'
import { TIMELINE_PAGE_SIZE } from './posts'

/** 通知の種類（バックエンドの NotificationType の定数名） */
export type NotificationType =
  | 'LIKED'
  | 'FOLLOWED'
  | 'CONSULTATION_REQUESTED'
  | 'CONSULTATION_APPROVED'
  | 'CONSULTATION_REJECTED'
  | 'MESSAGE_RECEIVED'

/** 通知の1件（バックエンドの NotificationListResponse.Item）。文面は画面側で type と actor から組み立てる */
export type NotificationItem = {
  id: number
  type: NotificationType
  /** いいね・フォロー・相談・メッセージをした人 */
  actor: { id: number; username: string; profileImageUrl: string | null }
  /** いいねの通知のみ。それ以外は null */
  post: { id: number; thumbnailUrl: string | null } | null
  /** 相談・メッセージの通知のみ。それ以外は null */
  conversationId: number | null
  notifiedAt: string
  read: boolean
}

/** 通知一覧（1ページ分。バックエンドの NotificationListResponse） */
export type NotificationListResponse = {
  /** 通知日時の新しい順 */
  notifications: NotificationItem[]
  page: number
  size: number
  hasNext: boolean
}

/** 自分の通知一覧（GET /api/notifications） */
export function fetchNotifications(page: number, token: string, signal?: AbortSignal): Promise<NotificationListResponse> {
  return getJson<NotificationListResponse>(`/api/notifications?page=${page}&size=${TIMELINE_PAGE_SIZE}`, token, signal)
}

/** 未読件数（GET /api/notifications/summary。ベルのバッジ用。メッセージの通知は含まない） */
export type NotificationSummary = {
  unreadCount: number
}

export function fetchNotificationSummary(token: string, signal?: AbortSignal): Promise<NotificationSummary> {
  return getJson<NotificationSummary>('/api/notifications/summary', token, signal)
}

/** 既読にする（read = true）／未読に戻す（read = false） */
export function setNotificationRead(id: number, read: boolean, token: string): Promise<void> {
  return sendWithToken<void>('POST', `/api/notifications/${id}/${read ? 'read' : 'unread'}`, token)
}

/** 通知を削除する */
export function deleteNotification(id: number, token: string): Promise<void> {
  return sendWithToken<void>('DELETE', `/api/notifications/${id}`, token)
}
