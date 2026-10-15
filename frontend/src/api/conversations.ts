import { getJson, postForm, sendWithToken } from './client'
import { TIMELINE_PAGE_SIZE } from './posts'

/** 会話の状態（バックエンドの ConversationStatus の定数名） */
export type ConversationStatus = 'REQUESTED' | 'ACTIVE' | 'ENDED' | 'REJECTED'

/**
 * DM一覧の絞り込み（GET /api/conversations の status）。
 * chats は進行中と終了（DM一覧の画面）、received は受け取った申請、sent は送った申請
 */
export type ConversationFilter = 'chats' | 'received' | 'sent'

/** 相手のユーザー（バックエンドの ConversationListResponse.Partner） */
export type ConversationPartner = {
  id: number
  username: string
  displayName: string
  /** 未設定は null */
  profileImageUrl: string | null
}

/** DM一覧の1件（バックエンドの ConversationListResponse.Item） */
export type ConversationListItem = {
  conversationId: number
  status: ConversationStatus
  /** ログイン中のユーザーが申し込んだ会話か */
  requestedByMe: boolean
  partner: ConversationPartner
  /** 最新メッセージの本文。メッセージがない場合・画像だけの場合は null */
  lastMessageBody: string | null
  lastMessageHasImage: boolean
  /** 最新メッセージの送信日時。メッセージがなければ null */
  lastMessageAt: string | null
  /** 相手から届いた未読メッセージの件数 */
  unreadCount: number
  requestedAt: string
  /** 相手から届いた未読メッセージのうち最新のもの（自分が送ったものは含まない）。unreadOnly を指定したときだけ入る */
  latestUnread: UnreadMessage | null
  /** 自分がピン留めしているか（相手の設定は返らない。docs/dm.md「ピン留め」） */
  pinned: boolean
  /** 自分が通知をオフにしているか（相手の設定は返らない。docs/dm.md「通知オフ」） */
  muted: boolean
}

/** 相手から届いた未読メッセージ（バックエンドの ConversationListResponse.UnreadMessage） */
export type UnreadMessage = {
  /** 本文。画像だけのメッセージは null */
  body: string | null
  hasImage: boolean
  sentAt: string
}

/** バックエンドの ConversationListResponse（1ページ分） */
export type ConversationListResponse = {
  conversations: ConversationListItem[]
  page: number
  size: number
  hasNext: boolean
}

/**
 * query は相手のユーザー名・表示名の一部で、空なら絞り込まない。
 * options.unreadOnly を true にすると、相手から届いた未読メッセージがある会話だけ（ホームの新着メッセージ）
 */
export function fetchConversations(
  filter: ConversationFilter,
  query: string,
  page: number,
  token: string,
  signal?: AbortSignal,
  options: { unreadOnly?: boolean; pinnedOnly?: boolean; size?: number } = {},
): Promise<ConversationListResponse> {
  const params = new URLSearchParams({
    status: filter,
    page: String(page),
    size: String(options.size ?? TIMELINE_PAGE_SIZE),
  })
  if (query.trim() !== '') params.set('q', query.trim())
  if (options.unreadOnly) params.set('unread', 'true')
  if (options.pinnedOnly) params.set('pinned', 'true')
  return getJson<ConversationListResponse>(`/api/conversations?${params}`, token, signal)
}

/** DM の件数（バックエンドの ConversationSummaryResponse） */
export type ConversationSummary = {
  /** やり取り中の会話（進行中・終了）で、相手から届いた未読メッセージの合計 */
  unreadMessageCount: number
  /** 受け取った申請（メッセージリクエスト）の件数 */
  receivedRequestCount: number
  /** 自分が送った申請（送信したリクエスト）の件数 */
  sentRequestCount: number
  /** 自分がピン留めしている相談の数（DM一覧の見出しのマークのバッジ。未読ではない） */
  pinnedCount: number
}

export function fetchConversationSummary(token: string, signal?: AbortSignal): Promise<ConversationSummary> {
  return getJson<ConversationSummary>('/api/conversations/summary', token, signal)
}

/** 会話1件（バックエンドの ConversationDetailResponse） */
export type ConversationDetail = {
  conversationId: number
  status: ConversationStatus
  requestedByMe: boolean
  partner: ConversationPartner
  requestedAt: string
  respondedAt: string | null
  endedAt: string | null
  /**
   * 30日やり取りがなかったため自動で終了したか（docs/dm.md「30日やり取りがない相談の自動終了」）。
   * サーバーは終了の理由そのもの（ブロック・利用停止など）を返さない。相手に伝わらないようにするため
   */
  endedByInactivity: boolean
  /** 自分がピン留めしているか */
  pinned: boolean
  /** 自分が通知をオフにしているか */
  muted: boolean
}

/** 自分の、相談ごとの設定（バックエンドの ConversationSettingResponse）。相手の設定は返らない */
export type ConversationSetting = {
  conversationId: number
  pinned: boolean
  muted: boolean
}

/**
 * 相談をピン留めする（docs/dm.md「ピン留め」）。相手には分からない。
 * 失敗時は ApiError（409: 申請中・拒否の会話（INVALID_STATUS）・上限10件（PIN_LIMIT_REACHED））を投げる
 */
export function pinConversation(id: number, token: string): Promise<ConversationSetting> {
  return sendWithToken<ConversationSetting>('POST', `/api/conversations/${id}/pin`, token)
}

/** ピン留めを外す（していなくても成功する） */
export function unpinConversation(id: number, token: string): Promise<ConversationSetting> {
  return sendWithToken<ConversationSetting>('DELETE', `/api/conversations/${id}/pin`, token)
}

/**
 * 相談の通知をオフにする（docs/dm.md「通知オフ」）。メッセージは届くが、DM のバッジ・ホームの新着メッセージに数えず、
 * メッセージの通知も作らない。相手には分からない。進行中だけ（それ以外は ApiError 409）
 */
export function muteConversation(id: number, token: string): Promise<ConversationSetting> {
  return sendWithToken<ConversationSetting>('POST', `/api/conversations/${id}/mute`, token)
}

/** 通知をオンに戻す（会話の状態に関係なくできる） */
export function unmuteConversation(id: number, token: string): Promise<ConversationSetting> {
  return sendWithToken<ConversationSetting>('DELETE', `/api/conversations/${id}/mute`, token)
}

/** 当事者でなければ 404（存在しない会話と同じ） */
export function fetchConversation(id: number, token: string, signal?: AbortSignal): Promise<ConversationDetail> {
  return getJson<ConversationDetail>(`/api/conversations/${id}`, token, signal)
}

/** 承認・拒否の結果（バックエンドの ConversationResponse） */
export type ConversationStateResponse = {
  conversationId: number
  status: ConversationStatus
  requestedAt: string
  respondedAt: string | null
  endedAt: string | null
}

/**
 * 申請を承認する（accept）・拒否する（reject）。
 * 同じ申請に2回送ると、2回目は 409（INVALID_STATUS）になるため、画面側で押したらボタンを無効にすること
 */
export function respondToRequest(
  id: number,
  action: 'accept' | 'reject',
  token: string,
): Promise<ConversationStateResponse> {
  return sendWithToken<ConversationStateResponse>('POST', `/api/conversations/${id}/${action}`, token)
}

/** メッセージ1件（バックエンドの MessageResponse） */
export type Message = {
  id: number
  senderId: number
  /** ログイン中のユーザーが送ったものか */
  mine: boolean
  /** 本文。画像だけのメッセージは null */
  body: string | null
  /** 画像の URL。画像がなければ null */
  imageUrl: string | null
  sentAt: string
  /** 相手が読んだ日時。未読は null */
  readAt: string | null
}

/** バックエンドの MessageListResponse。messages は古い順 */
export type MessageListResponse = {
  messages: Message[]
  /** before（または指定なし）ではさらに古いものがあるか、after ではさらに新しいものがあるか */
  hasMore: boolean
}

/** 1回に読むメッセージの件数 */
export const MESSAGE_PAGE_SIZE = 30

/**
 * 会話のメッセージ（古い順）。何も指定しなければ最新の MESSAGE_PAGE_SIZE 件。
 * before はそれより古いもの（上にスクロールしたとき）、after はそれより新しいもの（開いている間の取り直し）。
 * 読むと、相手から届いた未読メッセージは既読になる
 */
export function fetchMessages(
  id: number,
  range: { before?: number; after?: number },
  token: string,
  signal?: AbortSignal,
): Promise<MessageListResponse> {
  const params = new URLSearchParams({ size: String(MESSAGE_PAGE_SIZE) })
  if (range.before !== undefined) params.set('before', String(range.before))
  if (range.after !== undefined) params.set('after', String(range.after))
  return getJson<MessageListResponse>(`/api/conversations/${id}/messages?${params}`, token, signal)
}

/** メッセージを送る。本文（body）と画像（image）のどちらか一方は必須。進行中の会話だけ送れる */
export function sendMessage(id: number, body: string, image: File | null, token: string): Promise<Message> {
  const form = new FormData()
  if (body.trim() !== '') form.set('body', body)
  if (image) form.set('image', image)
  return postForm<Message>(`/api/conversations/${id}/messages`, form, token)
}

/** 会話を終了する（進行中 → 終了）。取り消せない */
export function endConversation(id: number, token: string): Promise<ConversationStateResponse> {
  return sendWithToken<ConversationStateResponse>('POST', `/api/conversations/${id}/end`, token)
}
