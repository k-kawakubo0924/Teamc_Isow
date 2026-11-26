import { getJson, sendWithToken } from './client'

/*
 * ミュート（投稿の非表示。docs/profile.md「ミュート（投稿の非表示）」）。
 * 相手の投稿を、自分のホーム・検索などに流れてこなくするだけで、何も消さず、相手には分からない。
 * DM の「通知オフ」（conversations.ts の muteConversation）とは別のもの。
 * 画面のメニューは「非表示」「非表示を解除」。「ミュート」の語は画面に出さない（文言は components/mute/muteText.ts）
 */

/** ミュートしている相手1人（バックエンドの MuteListResponse.Item） */
export type MutedUser = {
  userId: number
  username: string
  displayName: string
  profileImageUrl: string | null
  /** ミュートした日時（タイムゾーンなしの日時の文字列） */
  mutedAt: string
}

/** ミュートしている人の一覧（1ページ分。ミュートした新しい順） */
export type MuteListResponse = {
  items: MutedUser[]
  page: number
  size: number
  hasNext: boolean
}

/** ミュートしている人の一覧で1回に読み込む件数 */
export const MUTE_PAGE_SIZE = 20

/** ミュートする（POST /api/users/{id}/mute）。すでにミュートしていても成功 */
export function muteUser(userId: number, token: string): Promise<void> {
  return sendWithToken<void>('POST', `/api/users/${userId}/mute`, token)
}

/** ミュートを解除する（DELETE /api/users/{id}/mute）。ミュートしていなくても成功 */
export function unmuteUser(userId: number, token: string): Promise<void> {
  return sendWithToken<void>('DELETE', `/api/users/${userId}/mute`, token)
}

/** 自分がミュートしている人の一覧（GET /api/mutes） */
export function fetchMutes(page: number, token: string, signal?: AbortSignal): Promise<MuteListResponse> {
  return getJson<MuteListResponse>(`/api/mutes?page=${page}&size=${MUTE_PAGE_SIZE}`, token, signal)
}
