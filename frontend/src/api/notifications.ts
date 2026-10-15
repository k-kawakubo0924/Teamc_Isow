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
  /** 運営が投稿を非公開にした・公開に戻した（docs/admin.md「6. 措置」）。「誰が」（actor）はない */
  | 'POST_HIDDEN'
  | 'POST_UNHIDDEN'

/** 通知の1件（バックエンドの NotificationListResponse.Item）。文面は画面側で type と actor から組み立てる */
export type NotificationItem = {
  id: number
  type: NotificationType
  /** いいね・フォロー・相談・メッセージをした人。運営からの通知（POST_HIDDEN など）は null */
  actor: { id: number; username: string; profileImageUrl: string | null } | null
  /** いいね・運営からの投稿の通知のみ。それ以外は null */
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

/** 未読件数（GET /api/notifications/summary。ベルとタブのバッジ用） */
export type NotificationSummary = {
  /** 未読の通知（「いいね・フォロー」タブ）。メッセージの通知は含まない（DM のバッジで数えるため） */
  unreadCount: number
  /** 未読のお知らせ（「お知らせ」タブ）。取り消したもの・登録より前に発行されたものは含まない */
  unreadAnnouncementCount: number
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
