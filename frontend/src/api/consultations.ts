import { getJson, postJson } from './client'
import type { ConversationStatus } from './conversations'

/** 相談を申し込めない理由（バックエンドの ConsultationUnavailableReason の定数名） */
export type ConsultationUnavailableReason =
  | 'SELF'
  | 'ALREADY_REQUESTED'
  | 'REQUEST_RECEIVED'
  | 'IN_PROGRESS'
  | 'REJECTED_RECENTLY'
  | 'LIMIT_REACHED'

/** 相手に相談を申し込めるか（バックエンドの ConsultationStatusResponse） */
export type ConsultationStatus = {
  available: boolean
  /** 申し込める場合は null */
  reason: ConsultationUnavailableReason | null
  /** 申し込めない理由の表示文言。申し込める場合は null */
  message: string | null
  /** 再度申し込めるようになる日時（REJECTED_RECENTLY のときだけ） */
  availableAt: string | null
  /** 申請中・進行中の会話の ID（ALREADY_REQUESTED・REQUEST_RECEIVED・IN_PROGRESS のときだけ） */
  conversationId: number | null
}

/** 1人分（プロフィール画面） */
export function fetchConsultationStatus(userId: number, token: string, signal?: AbortSignal): Promise<ConsultationStatus> {
  return getJson<ConsultationStatus>(`/api/users/${userId}/consultation-status`, token, signal)
}

/** まとめて確認できる人数の上限（バックエンドの ConsultationService.MAX_STATUS_USERS） */
export const MAX_STATUS_USERS = 50

/**
 * 複数人分をまとめて確認する（フォロー中一覧。1人ずつ呼ぶと人数分の通信になるため）。
 * 結果はユーザー ID → 状態。存在しないユーザーは含まれない
 */
export async function fetchConsultationStatuses(
  userIds: number[],
  token: string,
  signal?: AbortSignal,
): Promise<Map<number, ConsultationStatus>> {
  const result = new Map<number, ConsultationStatus>()
  for (let i = 0; i < userIds.length; i += MAX_STATUS_USERS) {
    const ids = userIds.slice(i, i + MAX_STATUS_USERS).join(',')
    const response = await getJson<{ statuses: { userId: number; status: ConsultationStatus }[] }>(
      `/api/users/consultation-statuses?ids=${ids}`,
      token,
      signal,
    )
    for (const item of response.statuses) result.set(item.userId, item.status)
  }
  return result
}

/** 申込の結果（バックエンドの ConsultationResponse） */
export type ConsultationResponse = {
  conversationId: number
  status: ConversationStatus
  requestedAt: string
}

/** 相談を申し込む。message は一言メッセージ（任意。空なら送らない） */
export function requestConsultation(
  recipientId: number,
  message: string,
  token: string,
): Promise<ConsultationResponse> {
  return postJson<ConsultationResponse>('/api/conversations', { recipientId, message }, token)
}
